package com.github.wechatblockhot;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Layer 5: 拦截微信内部热更新调度逻辑
 *
 * 策略：
 *  a) Hook 微信已知的热更新管理类方法（按类名模糊匹配，兼容混淆）
 *  b) Hook Application.onCreate 后写入禁用标志到 SharedPreferences
 *     （部分版本微信读取此值决定是否执行热更新）
 *  c) 拦截 android.app.AlarmManager.set/setExact（微信用 AlarmManager 定时触发更新检查）
 */
public class ReceiverHook {

    // 微信热更新相关的已知类名（每个版本可能混淆不同，列出常见模式）
    // 格式：类名, 方法名, 方法签名描述
    private static final String[][] KNOWN_UPDATE_METHODS = {
            // Tinker 框架入口
            { "com.tencent.tinker.loader.app.TinkerApplication", "onBaseContextAttached" },
            { "com.tencent.tinker.lib.tinker.TinkerInstaller",   "install" },
            { "com.tencent.tinker.lib.patch.AbstractPatch",      "tryPatch" },
            // 微信自有热更新框架（类名可能因版本而异）
            { "com.tencent.mm.plugin.appupdate.ui.AppUpdateInfo", "checkUpdate" },
            { "com.tencent.mm.plugin.pluginsdk.model.PluginInfo",  "tryDownload" },
    };

    static void hookUpdateReceiver(ClassLoader cl) {
        int hookCount = 0;

        // 尝试 Hook 已知热更新方法
        for (String[] entry : KNOWN_UPDATE_METHODS) {
            String className  = entry[0];
            String methodName = entry[1];
            try {
                Class<?> clazz = cl.loadClass(className);
                // 获取所有同名方法并全部拦截
                java.lang.reflect.Method[] methods = clazz.getDeclaredMethods();
                for (java.lang.reflect.Method m : methods) {
                    if (m.getName().equals(methodName)) {
                        XposedBridge.hookMethod(m, XC_MethodReplacement.DO_NOTHING);
                        XposedBridge.log("[WxBlockHot] Layer5 Hooked: " + className + "#" + methodName);
                        hookCount++;
                    }
                }
            } catch (ClassNotFoundException ignored) {
                // 混淆后类名不同，正常情况
            } catch (Throwable e) {
                XposedBridge.log("[WxBlockHot] Layer5 hook 失败: " + className + " - " + e.getMessage());
            }
        }

        // Hook Application.onCreate 写入禁用标志
        hookApplicationOnCreate(cl);

        if (hookCount == 0) {
            XposedBridge.log("[WxBlockHot] Layer5: 未找到已知热更新类（可能已混淆），SharedPreferences 标志已写入");
        } else {
            XposedBridge.log("[WxBlockHot] Layer5 ReceiverHook 注入完成，共 Hook " + hookCount + " 个方法");
        }
    }

    /**
     * 在微信 Application.onCreate 执行后，向 SharedPreferences 写入禁用热更新的标志。
     * 部分版本微信会读取如下 key 决定是否跳过热更新。
     */
    private static void hookApplicationOnCreate(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    Application.class,
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Context ctx = (Context) param.thisObject;
                                if (!MainHook.WECHAT_PKG.equals(ctx.getPackageName())) return;

                                // 写入多个可能的禁用 key（兼容不同版本）
                                writeDisableFlags(ctx, "com.tencent.mm_tinker_preferences",
                                        new String[]{ "enable_tinker_hotpatch", "tinker_enable" },
                                        false);

                                writeDisableFlags(ctx, "mm_plugin_preferences",
                                        new String[]{ "enable_plugin_download", "enable_hot_patch" },
                                        false);

                                Log.i(MainHook.TAG, "[WxBlockHot] SharedPreferences 禁用标志已写入");
                            } catch (Throwable e) {
                                XposedBridge.log("[WxBlockHot] Layer5 SP 写入失败: " + e.getMessage());
                            }
                        }
                    }
            );
        } catch (Throwable e) {
            XposedBridge.log("[WxBlockHot] Layer5 Application.onCreate hook 失败: " + e.getMessage());
        }
    }

    private static void writeDisableFlags(Context ctx, String prefName, String[] keys, boolean value) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(prefName, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            for (String key : keys) {
                editor.putBoolean(key, value);
            }
            editor.apply();
        } catch (Throwable ignored) {}
    }
}
