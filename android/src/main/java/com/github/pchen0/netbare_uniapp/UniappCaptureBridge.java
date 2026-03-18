/*
 * 桥接层：在 NetBare 注入线程与 UniApp JS 主线程之间传递请求/响应体，并等待 JS 返回是否修改。
 * 与 NetBareModule 配合使用。
 */
package com.github.pchen0.netbare_uniapp;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 供 UniappCaptureInjector 调用：将请求/响应体发给 JS，阻塞等待 JS 返回修改后的 body（或超时使用原样）。
 */
public class UniappCaptureBridge {

    private static final int DEFAULT_WAIT_MS = 10000;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // body 拦截等待队列
    private final ConcurrentHashMap<String, LinkedBlockingQueue<byte[]>> pendingBodies =
            new ConcurrentHashMap<>();
    // header 拦截等待队列（内容为 headers 的 JSON 字符串）
    private final ConcurrentHashMap<String, LinkedBlockingQueue<String>> pendingHeaders =
            new ConcurrentHashMap<>();

    private volatile UniappCaptureBridge.JsHandler jsHandler;
    private volatile List<String> urlPrefixes = new ArrayList<>(); // 空表示不过滤，全部拦截

    public static UniappCaptureBridge getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        static final UniappCaptureBridge INSTANCE = new UniappCaptureBridge();
    }

    /** 由 Module 设置，用于把数据抛给 JS */
    public interface JsHandler {
        void invokeOnJs(String callbackId,
                        String type,
                        String url,
                        String methodOrNull,
                        Integer statusCodeOrNull,
                        String bodyBase64,
                        String headersJson);
    }

    public void setJsHandler(JsHandler jsHandler) {
        this.jsHandler = jsHandler;
    }

    /** 设置要拦截的 URL 前缀列表，空或 null 表示拦截全部 */
    public void setUrlPrefixes(List<String> prefixes) {
        this.urlPrefixes = prefixes != null ? new ArrayList<>(prefixes) : new ArrayList<>();
    }

    public boolean shouldInterceptUrl(String url) {
        if (url == null) return false;
        if (urlPrefixes.isEmpty()) return true;
        for (String prefix : urlPrefixes) {
            if (prefix != null && url.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * 注入线程调用：把请求体发给 JS，阻塞等待修改后的 body；超时则返回 originalBody。
     */
    public byte[] waitForRequestModification(String callbackId, String url, String method, byte[] originalBody) {
        return waitForBodyModification(callbackId, "request", url, method, null, originalBody);
    }

    /**
     * 注入线程调用：把响应体发给 JS，阻塞等待修改后的 body；超时则返回 originalBody。
     */
    public byte[] waitForResponseModification(String callbackId, String url, int statusCode, byte[] originalBody) {
        return waitForBodyModification(callbackId, "response", url, null, statusCode, originalBody);
    }

    private byte[] waitForBodyModification(String callbackId,
                                           String type,
                                           String url,
                                           String method,
                                           Integer statusCode,
                                           byte[] originalBody) {
        String bodyBase64 = originalBody != null && originalBody.length > 0
                ? Base64.encodeToString(originalBody, Base64.NO_WRAP) : "";
        LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>(1);
        pendingBodies.put(callbackId, queue);

        JsHandler handler = jsHandler;
        if (handler != null) {
            mainHandler.post(() -> {
                try {
                    handler.invokeOnJs(callbackId, type, url, method, statusCode, bodyBase64, null);
                } catch (Throwable ignored) { }
            });
        }

        try {
            byte[] result = queue.poll(DEFAULT_WAIT_MS, TimeUnit.MILLISECONDS);
            return (result != null) ? result : originalBody;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return originalBody;
        } finally {
            pendingBodies.remove(callbackId);
        }
    }

    /**
     * 注入线程调用：把请求头发给 JS，阻塞等待修改后的 headersJson；超时则返回 originalJson。
     */
    public String waitForRequestHeaderModification(String callbackId,
                                                   String url,
                                                   String method,
                                                   String originalHeadersJson) {
        return waitForHeaderModification(callbackId, "requestHeader", url, method, null, originalHeadersJson);
    }

    /**
     * 注入线程调用：把响应头发给 JS，阻塞等待修改后的 headersJson；超时则返回 originalJson。
     */
    public String waitForResponseHeaderModification(String callbackId,
                                                    String url,
                                                    int statusCode,
                                                    String originalHeadersJson) {
        return waitForHeaderModification(callbackId, "responseHeader", url, null, statusCode, originalHeadersJson);
    }

    private String waitForHeaderModification(String callbackId,
                                             String type,
                                             String url,
                                             String method,
                                             Integer statusCode,
                                             String originalHeadersJson) {
        LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(1);
        pendingHeaders.put(callbackId, queue);

        JsHandler handler = jsHandler;
        if (handler != null) {
            mainHandler.post(() -> {
                try {
                    handler.invokeOnJs(callbackId, type, url, method, statusCode, null, originalHeadersJson);
                } catch (Throwable ignored) { }
            });
        }

        try {
            String result = queue.poll(DEFAULT_WAIT_MS, TimeUnit.MILLISECONDS);
            return (result != null) ? result : originalHeadersJson;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return originalHeadersJson;
        } finally {
            pendingHeaders.remove(callbackId);
        }
    }

    /**
     * 由 Module 在 JS 调用 setRequestModification 时调用，解除阻塞并返回修改后的 body。
     */
    public void deliverRequestModification(String callbackId, String modifiedBodyBase64) {
        deliverBodyModification(callbackId, modifiedBodyBase64);
    }

    /**
     * 由 Module 在 JS 调用 setResponseModification 时调用。
     */
    public void deliverResponseModification(String callbackId, String modifiedBodyBase64) {
        deliverBodyModification(callbackId, modifiedBodyBase64);
    }

    private void deliverBodyModification(String callbackId, String modifiedBodyBase64) {
        LinkedBlockingQueue<byte[]> queue = pendingBodies.get(callbackId);
        if (queue == null) return;
        if (modifiedBodyBase64 == null || modifiedBodyBase64.isEmpty()) {
            queue.offer(null);
            return;
        }
        try {
            byte[] decoded = Base64.decode(modifiedBodyBase64, Base64.NO_WRAP);
            queue.offer(decoded);
        } catch (Throwable ignored) {
            queue.offer(null);
        }
    }

    /**
     * 由 Module 在 JS 调用 setRequestHeaderModification 时调用，解除阻塞并返回修改后的 headersJson。
     */
    public void deliverRequestHeaderModification(String callbackId, String headersJson) {
        deliverHeaderModification(callbackId, headersJson);
    }

    /**
     * 由 Module 在 JS 调用 setResponseHeaderModification 时调用。
     */
    public void deliverResponseHeaderModification(String callbackId, String headersJson) {
        deliverHeaderModification(callbackId, headersJson);
    }

    private void deliverHeaderModification(String callbackId, String headersJson) {
        LinkedBlockingQueue<String> queue = pendingHeaders.get(callbackId);
        if (queue == null) return;
        queue.offer(headersJson);
    }
}
