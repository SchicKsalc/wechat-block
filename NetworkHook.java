package com.github.wechatblockhot;

import android.util.Log;

import java.io.IOException;
import java.net.URL;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Layer 1: 拦截 java.net.URL.openConnection()
 *
 * 作用：在最底层网络连接建立前，对热更新域名的请求抛出 IOException，
 *       使微信热更新逻辑无法获取到任何响应数据。
 */
public class NetworkHook {

    static void hookUrlOpenConnection(ClassLoader cl) {
        try {
            // Hook URL.openConnection()（无参版本）
            XposedHelpers.findAndHookMethod(
                    URL.class,
                    "openConnection",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            URL url = (URL) param.thisObject;
                            if (BlockedDomains.isBlockedUrl(url.toString())) {
                                String msg = "[WxBlockHot] Blocked URL: " + url.getHost();
                                Log.i(MainHook.TAG, msg);
                                XposedBridge.log(msg);
                                param.setThrowable(
                                        new IOException("WeChat hot-update blocked: " + url.getHost())
                                );
                            }
                        }
                    }
            );

            // Hook URL.openConnection(Proxy)（有代理版本）
            XposedHelpers.findAndHookMethod(
                    URL.class,
                    "openConnection",
                    java.net.Proxy.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            URL url = (URL) param.thisObject;
                            if (BlockedDomains.isBlockedUrl(url.toString())) {
                                String msg = "[WxBlockHot] Blocked URL(proxy): " + url.getHost();
                                Log.i(MainHook.TAG, msg);
                                XposedBridge.log(msg);
                                param.setThrowable(
                                        new IOException("WeChat hot-update blocked: " + url.getHost())
                                );
                            }
                        }
                    }
            );

            XposedBridge.log("[WxBlockHot] Layer1 NetworkHook (URL) 注入成功");

        } catch (Throwable e) {
            XposedBridge.log("[WxBlockHot] Layer1 NetworkHook 失败: " + e.getMessage());
        }
    }
}
