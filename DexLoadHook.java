package com.github.wechatblockhot;

import android.util.Log;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Layer 4: 拦截 DexClassLoader / BaseDexClassLoader 构造函数
 *
 * 即使热补丁已写入磁盘，本层也会阻止微信通过 DexClassLoader
 * 动态加载 .dex / .apk / .jar 补丁文件。
 *
 * 微信 Tinker 框架典型加载路径：
 *   new DexClassLoader(patchDexPath, optimizedDir, ...)
 */
public class DexLoadHook {

    static void hookDexClassLoader(ClassLoader cl) {

        // Hook DexClassLoader 构造函数
        hookDexClassLoaderConstructor(DexClassLoader.class);

        // Hook BaseDexClassLoader（DexClassLoader 的父类）
        try {
            Class<?> baseDex = Class.forName("dalvik.system.BaseDexClassLoader");
            hookDexClassLoaderConstructor(baseDex);
        } catch (Throwable ignored) {}

        // Hook InMemoryDexClassLoader（Android 8.0+ 内存加载方式）
        try {
            Class<?> inMemory = Class.forName("dalvik.system.InMemoryDexClassLoader");
            // InMemoryDexClassLoader 使用 ByteBuffer，路径检测不适用
            // 此处仅记录调用，不拦截（防止误伤正常功能）
            XposedBridge.log("[WxBlockHot] InMemoryDexClassLoader 已检测到但不拦截");
        } catch (Throwable ignored) {}

        XposedBridge.log("[WxBlockHot] Layer4 DexLoadHook 注入成功");
    }

    private static void hookDexClassLoaderConstructor(Class<?> targetClass) {
        try {
            XposedHelpers.findAndHookConstructor(
                    targetClass,
                    String.class,   // dexPath
                    String.class,   // optimizedDirectory
                    String.class,   // librarySearchPath
                    ClassLoader.class, // parent
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String dexPath = (String) param.args[0];
                            if (dexPath != null && BlockedDomains.isBlockedFilePath(dexPath)) {
                                String msg = "[WxBlockHot] Blocked DEX load: " + dexPath;
                                Log.e(MainHook.TAG, msg);
                                XposedBridge.log(msg);
                                // 替换为空路径，使 ClassLoader 无法找到补丁类
                                param.args[0] = "/dev/null";
                            }
                        }
                    }
            );
        } catch (Throwable e) {
            XposedBridge.log("[WxBlockHot] Layer4 DexClassLoader hook 失败(" +
                    targetClass.getSimpleName() + "): " + e.getMessage());
        }
    }
}
