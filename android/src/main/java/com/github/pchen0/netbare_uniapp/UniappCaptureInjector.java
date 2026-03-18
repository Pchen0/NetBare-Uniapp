/*
 * 根据 URL 过滤并抓取请求/响应体与 Header，通过 UniappCaptureBridge 交给 JS 并支持返回修改后的内容。
 */
package com.github.pchen0.netbare_uniapp;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.megatronking.netbare.http.HttpBody;
import com.github.megatronking.netbare.http.HttpRequest;
import com.github.megatronking.netbare.http.HttpRequestHeaderPart;
import com.github.megatronking.netbare.http.HttpResponse;
import com.github.megatronking.netbare.http.HttpResponseHeaderPart;
import com.github.megatronking.netbare.injector.InjectorCallback;
import com.github.megatronking.netbare.injector.SimpleHttpInjector;
import com.github.megatronking.netbare.stream.ByteStream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UniappCaptureInjector extends SimpleHttpInjector {

    private final UniappCaptureBridge bridge = UniappCaptureBridge.getInstance();

    @Override
    public boolean sniffRequest(@NonNull HttpRequest request) {
        return bridge.shouldInterceptUrl(request.url());
    }

    @Override
    public boolean sniffResponse(@NonNull HttpResponse response) {
        return bridge.shouldInterceptUrl(response.url());
    }

    @Override
    public void onRequestInject(@NonNull HttpRequestHeaderPart header,
                                @NonNull InjectorCallback callback) throws IOException {
        String url = header.uri() != null ? header.uri().toString() : "";
        String method = header.method() != null ? header.method().name() : null;
        String headersJson = toHeadersJson(header.headers());
        String callbackId = UUID.randomUUID().toString();
        String modifiedJson = bridge.waitForRequestHeaderModification(callbackId, url, method, headersJson);
        if (modifiedJson == null || modifiedJson.isEmpty() || modifiedJson.equals(headersJson)) {
            callback.onFinished(header);
            return;
        }
        try {
            HttpRequestHeaderPart.Builder builder = header.newBuilder();
            builder.removeHeaders();
            JSONArray arr = JSON.parseArray(modifiedJson);
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (obj == null) continue;
                String name = obj.getString("name");
                String value = obj.getString("value");
                if (name != null && value != null) {
                    builder.addHeader(name, value);
                }
            }
            callback.onFinished(builder.build());
        } catch (Exception e) {
            callback.onFinished(header);
        }
    }

    @Override
    public void onResponseInject(@NonNull HttpResponseHeaderPart header,
                                 @NonNull InjectorCallback callback) throws IOException {
        String url = header.uri() != null ? header.uri().toString() : "";
        String headersJson = toHeadersJson(header.headers());
        String callbackId = UUID.randomUUID().toString();
        String modifiedJson = bridge.waitForResponseHeaderModification(callbackId, url, header.code(), headersJson);
        if (modifiedJson == null || modifiedJson.isEmpty() || modifiedJson.equals(headersJson)) {
            callback.onFinished(header);
            return;
        }
        try {
            HttpResponseHeaderPart.Builder builder = header.newBuilder();
            builder.removeHeaders();
            JSONArray arr = JSON.parseArray(modifiedJson);
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (obj == null) continue;
                String name = obj.getString("name");
                String value = obj.getString("value");
                if (name != null && value != null) {
                    builder.addHeader(name, value);
                }
            }
            callback.onFinished(builder.build());
        } catch (Exception e) {
            callback.onFinished(header);
        }
    }

    @Override
    public void onRequestInject(@NonNull HttpRequest request,
                                @NonNull HttpBody body,
                                @NonNull InjectorCallback callback) throws IOException {
        ByteBuffer buf = body.toBuffer();
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        String callbackId = UUID.randomUUID().toString();
        byte[] modified = bridge.waitForRequestModification(callbackId,
                request.url(), request.method().name(), bytes);
        callback.onFinished(new ByteStream(modified));
    }

    @Override
    public void onResponseInject(@NonNull HttpResponse response,
                                 @NonNull HttpBody body,
                                 @NonNull InjectorCallback callback) throws IOException {
        ByteBuffer buf = body.toBuffer();
        byte[] bytes = new byte[buf.remaining()];
        buf.get(bytes);
        String callbackId = UUID.randomUUID().toString();
        byte[] modified = bridge.waitForResponseModification(callbackId,
                response.url(), response.code(), bytes);
        callback.onFinished(new ByteStream(modified));
    }

    private String toHeadersJson(Map<String, List<String>> headers) {
        JSONArray arr = new JSONArray();
        if (headers != null) {
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                String name = entry.getKey();
                List<String> values = entry.getValue();
                if (name == null || values == null) continue;
                for (String v : values) {
                    if (v == null) continue;
                    JSONObject obj = new JSONObject();
                    obj.put("name", name);
                    obj.put("value", v);
                    arr.add(obj);
                }
            }
        }
        return arr.toJSONString();
    }
}
