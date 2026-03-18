/*
 * NetBare UniApp Module 桥接类
 * 支持：抓包、按 URL 过滤、提取并修改请求/响应体与 Header，并通过 JSON 传入 NetBareConfig。
 */
package com.github.pchen0.netbare_uniapp;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.megatronking.netbare.NetBare;
import com.github.megatronking.netbare.NetBareConfig;
import com.github.megatronking.netbare.http.HttpInjectInterceptor;
import com.github.megatronking.netbare.http.HttpInterceptorFactory;
import com.github.megatronking.netbare.ip.IpAddress;
import com.github.megatronking.netbare.ssl.JKS;
import com.github.pchen0.netbare_uniapp.UniappCaptureBridge;
import com.github.pchen0.netbare_uniapp.UniappCaptureInjector;

import io.dcloud.feature.uniapp.annotation.UniJSMethod;
import io.dcloud.feature.uniapp.bridge.UniJSCallback;
import io.dcloud.feature.uniapp.common.UniModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetBareModule extends UniModule {

    private UniJSCallback mInterceptHandler;

    /** 绑定 Application，应在应用启动时调用一次。 */
    @UniJSMethod(uiThread = true)
    public void attachApplication(boolean debug, UniJSCallback callback) {
        try {
            if (mUniSDKInstance == null || mUniSDKInstance.getContext() == null) {
                if (callback != null) callback.invoke(false);
                return;
            }
            Application app = (Application) mUniSDKInstance.getContext().getApplicationContext();
            NetBare.get().attachApplication(app, debug);
            if (callback != null) callback.invoke(true);
        } catch (Throwable e) {
            if (callback != null) callback.invokeAndKeepAlive(e.getMessage());
        }
    }

    /**
     * 设置抓包回调。
     *  - 请求体：{ type: 'request', callbackId, url, method, body }
     *  - 响应体：{ type: 'response', callbackId, url, statusCode, body }
     *  - 请求头：{ type: 'requestHeader', callbackId, url, method, headersJson }
     *  - 响应头：{ type: 'responseHeader', callbackId, url, statusCode, headersJson }
     */
    @UniJSMethod(uiThread = true)
    public void setInterceptHandler(UniJSCallback callback) {
        mInterceptHandler = callback;
        UniappCaptureBridge bridge = UniappCaptureBridge.getInstance();
        bridge.setJsHandler((callbackId, type, url, methodOrNull, statusCodeOrNull, bodyBase64, headersJson) -> {
            if (mInterceptHandler == null) return;
            Map<String, Object> map = new HashMap<>();
            map.put("type", type);
            map.put("callbackId", callbackId);
            map.put("url", url);
            if (methodOrNull != null) map.put("method", methodOrNull);
            if (statusCodeOrNull != null) map.put("statusCode", statusCodeOrNull);
            if (bodyBase64 != null) map.put("body", bodyBase64);
            if (headersJson != null) map.put("headersJson", headersJson);
            mInterceptHandler.invokeAndKeepAlive(map);
        });
    }

    /** 设置要拦截的 URL 前缀列表 */
    @UniJSMethod(uiThread = true)
    public void setInterceptUrls(String urlPatternsJson, UniJSCallback callback) {
        try {
            List<String> list = new ArrayList<>();
            if (urlPatternsJson != null && !urlPatternsJson.trim().isEmpty()) {
                JSONArray arr = JSON.parseArray(urlPatternsJson);
                for (int i = 0; i < arr.size(); i++) {
                    String s = arr.getString(i);
                    if (s != null && !s.isEmpty()) list.add(s);
                }
            }
            UniappCaptureBridge.getInstance().setUrlPrefixes(list);
            if (callback != null) callback.invoke(true);
        } catch (Throwable e) {
            if (callback != null) callback.invoke(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /** 修改请求体（Base64）。 */
    @UniJSMethod(uiThread = true)
    public void setRequestModification(String callbackId, String modifiedBodyBase64, UniJSCallback callback) {
        UniappCaptureBridge.getInstance().deliverRequestModification(callbackId, modifiedBodyBase64);
        if (callback != null) callback.invoke(true);
    }

    /** 修改响应体（Base64）。 */
    @UniJSMethod(uiThread = true)
    public void setResponseModification(String callbackId, String modifiedBodyBase64, UniJSCallback callback) {
        UniappCaptureBridge.getInstance().deliverResponseModification(callbackId, modifiedBodyBase64);
        if (callback != null) callback.invoke(true);
    }

    /** 修改请求头（headersJson 为数组字符串，每项 {name,value}）。 */
    @UniJSMethod(uiThread = true)
    public void setRequestHeaderModification(String callbackId, String headersJson, UniJSCallback callback) {
        UniappCaptureBridge.getInstance().deliverRequestHeaderModification(callbackId, headersJson);
        if (callback != null) callback.invoke(true);
    }

    /** 修改响应头。 */
    @UniJSMethod(uiThread = true)
    public void setResponseHeaderModification(String callbackId, String headersJson, UniJSCallback callback) {
        UniappCaptureBridge.getInstance().deliverResponseHeaderModification(callbackId, headersJson);
        if (callback != null) callback.invoke(true);
    }

    /** 检查/申请 VPN 权限，并尽量拉起系统授权页。 */
    @UniJSMethod(uiThread = true)
    public void prepare(UniJSCallback callback) {
        try {
            Intent intent = NetBare.get().prepare();
            if (intent == null) {
                if (callback != null) {
                    callback.invoke(Collections.singletonMap("needPermission", false));
                }
                return;
            }
            boolean intentStarted = false;
            if (mUniSDKInstance != null && mUniSDKInstance.getContext() != null) {
                try {
                    android.content.Context ctx = mUniSDKInstance.getContext();
                    if (ctx instanceof Activity) {
                        ((Activity) ctx).startActivityForResult(intent, 0x0B00);
                        intentStarted = true;
                    } else {
                        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        intentStarted = true;
                    }
                } catch (Throwable ignored) { }
            }
            if (callback != null) {
                Map<String, Object> map = new HashMap<>();
                map.put("needPermission", true);
                map.put("intentStarted", intentStarted);
                if (!intentStarted) {
                    map.put("error", "无法获取 Activity，请在前端引导用户到系统设置中开启 VPN 权限");
                }
                callback.invoke(map);
            }
        } catch (Throwable e) {
            if (callback != null) callback.invokeAndKeepAlive(Collections.singletonMap("error", e.getMessage()));
        }
    }

    @UniJSMethod(uiThread = true)
    public void start(String jksAlias, String jksCn, UniJSCallback callback) {
        start(jksAlias, jksCn, null, callback);
    }

    /**
     * 启动 NetBare 服务（带抓包/修改注入器），支持通过 JSON 传入 NetBareConfig。
     *
     * @param jksAlias JKS 别名
     * @param jksCn    证书 CN
     * @param configJson 可选配置 JSON 字符串（见文档），可为 null
     */
    @UniJSMethod(uiThread = true)
    public void start(String jksAlias, String jksCn, String configJson, UniJSCallback callback) {
        try {
            if (mUniSDKInstance == null || mUniSDKInstance.getContext() == null) {
                if (callback != null) callback.invoke(Collections.singletonMap("error", "context is null"));
                return;
            }
            Application app = (Application) mUniSDKInstance.getContext().getApplicationContext();
            JKS jks = new JKS(app, jksAlias, jksCn.toCharArray(), jksCn, jksCn, jksCn, jksCn, jksCn);
            List<HttpInterceptorFactory> factories = new ArrayList<>();
            factories.add(HttpInjectInterceptor.createFactory(new UniappCaptureInjector()));

            NetBareConfig base = NetBareConfig.defaultHttpConfig(jks, factories);
            NetBareConfig config = applyConfigFromJson(base, configJson);

            NetBare.get().start(config);
            if (callback != null) callback.invoke(true);
        } catch (Throwable e) {
            if (callback != null) callback.invokeAndKeepAlive(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /** 停止 NetBare 服务。 */
    @UniJSMethod(uiThread = true)
    public void stop(UniJSCallback callback) {
        try {
            NetBare.get().stop();
            if (callback != null) callback.invoke(true);
        } catch (Throwable e) {
            if (callback != null) callback.invokeAndKeepAlive(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /** 当前 NetBare 是否已启动。 */
    @UniJSMethod(uiThread = false)
    public void isActive(UniJSCallback callback) {
        try {
            boolean active = NetBare.get().isActive();
            if (callback != null) callback.invoke(Collections.singletonMap("active", active));
        } catch (Throwable e) {
            if (callback != null) callback.invokeAndKeepAlive(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /** 从 JSON 中解析 NetBareConfig 的可配置字段。 */
    private NetBareConfig applyConfigFromJson(NetBareConfig base, String configJson) {
        if (configJson == null || configJson.trim().isEmpty()) {
            return base;
        }
        try {
            JSONObject obj = JSON.parseObject(configJson);
            if (obj == null || obj.isEmpty()) return base;

            NetBareConfig.Builder builder = base.newBuilder();

            if (obj.containsKey("session")) {
                String session = obj.getString("session");
                if (session != null && !session.isEmpty()) builder.setSession(session);
            }

            if (obj.containsKey("mtu")) {
                int mtu = obj.getIntValue("mtu");
                if (mtu > 0) builder.setMtu(mtu);
            }

            String address = obj.getString("address");
            IpAddress ipAddr = parseIpAddress(address);
            if (ipAddr != null) builder.setAddress(ipAddr);

            JSONArray routes = obj.getJSONArray("routes");
            if (routes != null) {
                for (int i = 0; i < routes.size(); i++) {
                    IpAddress r = parseIpAddress(routes.getString(i));
                    if (r != null) builder.addRoute(r);
                }
            }

            JSONArray dns = obj.getJSONArray("dnsServers");
            if (dns != null) {
                for (int i = 0; i < dns.size(); i++) {
                    String s = dns.getString(i);
                    if (s != null && !s.isEmpty()) builder.addDnsServer(s);
                }
            }

            JSONArray allowedApps = obj.getJSONArray("allowedApplications");
            if (allowedApps != null) {
                for (int i = 0; i < allowedApps.size(); i++) {
                    String p = allowedApps.getString(i);
                    if (p != null && !p.isEmpty()) builder.addAllowedApplication(p);
                }
            }

            JSONArray disallowedApps = obj.getJSONArray("disallowedApplications");
            if (disallowedApps != null) {
                for (int i = 0; i < disallowedApps.size(); i++) {
                    String p = disallowedApps.getString(i);
                    if (p != null && !p.isEmpty()) builder.addDisallowedApplication(p);
                }
            }

            JSONArray allowedHosts = obj.getJSONArray("allowedHosts");
            if (allowedHosts != null) {
                for (int i = 0; i < allowedHosts.size(); i++) {
                    String h = allowedHosts.getString(i);
                    if (h != null && !h.isEmpty()) builder.addAllowedHost(h);
                }
            }

            JSONArray disallowedHosts = obj.getJSONArray("disallowedHosts");
            if (disallowedHosts != null) {
                for (int i = 0; i < disallowedHosts.size(); i++) {
                    String h = disallowedHosts.getString(i);
                    if (h != null && !h.isEmpty()) builder.addDisallowedHost(h);
                }
            }

            if (obj.containsKey("dumpUid")) {
                builder.dumpUid(obj.getBooleanValue("dumpUid"));
            }
            if (obj.containsKey("excludeSelf")) {
                builder.excludeSelf(obj.getBooleanValue("excludeSelf"));
            }

            return builder.build();
        } catch (Throwable e) {
            // 出错则退回默认配置
            return base;
        }
    }

    /** 解析形如 \"10.1.10.1/32\" 或 \"10.1.10.1\" 的地址字符串为 IpAddress。 */
    private IpAddress parseIpAddress(String value) {
        if (value == null) return null;
        value = value.trim();
        if (value.isEmpty()) return null;
        try {
            String ip = value;
            int prefix = 32;
            int slash = value.indexOf('/');
            if (slash > 0 && slash < value.length() - 1) {
                ip = value.substring(0, slash);
                prefix = Integer.parseInt(value.substring(slash + 1));
            }
            return new IpAddress(ip, prefix);
        } catch (Exception e) {
            return null;
        }
    }
}
