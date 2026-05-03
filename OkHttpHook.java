package com.github.wechatblockhot;

import android.util.Log;

import java.io.IOException;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Layer 2: Hook 微信内嵌 OkHttp 的请求执行方法
 *
 * 微信将 OkHttp 打包在 APK 内部，包名可能为：
 *   - com.tencent.mars.xlog（Mars 网络库）
 *   - okhttp3.internal.connection.RealCall（标准 OkHttp3）
 *   - com.squareup.okhttp.internal.http.HttpEngine（OkHttp2）
 *
 * 策略：Hook RealCall.execute() 和 enqueue()，检查 Request URL，
 *       对热更新域名抛出 IOException 终止请求。
 */
public class OkHttpHook {

    // 微信可能使用的 OkHttp 类名（按优先级尝试）
    private static final String[] OKHTTP3_REALCALL_CLASSES = {
            "okhttp3.internal.connection.RealCall",          // OkHttp 4.x
            "okhttp3.RealCall",                              // OkHttp 3.x 旧版
            "com.tencent.okhttp3.internal.connection.RealCall", // 微信重命名版本
    };

    private static final String[] OKHTTP3_REQUEST_CLASSES = {
            "okhttp3.Request",
            "com.tencent.okhttp3.Request",
    };

    static void hookOkHttp(ClassLoader cl) {
        boolean hooked = false;

        // 尝试各种可能的 OkHttp 类名
        for (int i = 0; i < OKHTTP3_REALCALL_CLASSES.length; i++) {
            String callClass   = OKHTTP3_REALCALL_CLASSES[i];
            String requestClass = OKHTTP3_REQUEST_CLASSES[Math.min(i, OKHTTP3_REQUEST_CLASSES.length - 1)];

            if (tryHookRealCall(cl, callClass, requestClass)) {
                hooked = true;
                break;
            }
        }

        if (!hooked) {
            XposedBridge.log("[WxBlockHot] Layer2 OkHttpHook: 未找到 OkHttp，跳过（Layer1 已覆盖）");
        }
    }

    private static boolean tryHookRealCall(ClassLoader cl,
                                            String callClassName,
                                            String requestClassName) {
        try {
            Class<?> callClass    = cl.loadClass(callClassName);
            Class<?> requestClass = cl.loadClass(requestClassName);

            // ---- Hook execute()（同步请求）----
            XposedHelpers.findAndHookMethod(callClass, "execute", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    checkAndBlockRequest(param, requestClass, "execute");
                }
            });

            // ---- Hook getResponseWithInterceptorChain()（拦截链入口）----
            try {
                XposedHelpers.findAndHookMethod(
                        callClass, "getResponseWithInterceptorChain", new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                checkAndBlockRequest(param, requestClass, "interceptorChain");
                            }
                        });
            } catch (Throwable ignored) {} // 不是所有版本都有此方法

            XposedBridge.log("[WxBlockHot] Layer2 OkHttpHook 注入成功: " + callClassName);
            return true;

        } catch (Throwable e) {
            // 类不存在，继续尝试下一个
            return false;
        }
    }

    /**
     * 从 RealCall 实例中获取 Request.url，判断是否需要拦截
     */
    private static void checkAndBlockRequest(XC_MethodHook.MethodHookParam param,
                                              Class<?> requestClass,
                                              String methodName) throws IOException {
        try {
            // RealCall.originalRequest (OkHttp3) 或 RealCall.request
            Object request = null;
            try {
                request = XposedHelpers.getObjectField(param.thisObject, "originalRequest");
            } catch (Throwable e1) {
                try {
                    request = XposedHelpers.getObjectField(param.thisObject, "request");
                } catch (Throwable ignored) {}
            }

            if (request == null) return;

            // Request.url() 返回 HttpUrl 对象
            Object httpUrl = XposedHelpers.callMethod(request, "url");
            if (httpUrl == null) return;

            String urlStr = httpUrl.toString();
            if (BlockedDomains.isBlockedUrl(urlStr)) {
                String msg = "[WxBlockHot] OkHttp blocked [" + methodName + "]: " + urlStr;
                Log.i(MainHook.TAG, msg);
                XposedBridge.log(msg);
                throw new IOException("WeChat hot-update OkHttp blocked: " + urlStr);
            }
        } catch (IOException rethrow) {
            throw rethrow;
        } catch (Throwable ignored) {
            // 反射失败不影响正常逻辑
        }
    }
}
