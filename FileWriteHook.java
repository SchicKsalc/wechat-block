package com.github.wechatblockhot;

import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Layer 3: 拦截 FileOutputStream 构造函数
 *
 * 即使网络请求绕过了 Layer 1/2（例如使用 native socket），
 * 本层也会阻止热补丁文件被写入磁盘（写入抛出 FileNotFoundException）。
 *
 * 补丁写入的典型路径（Android/data/com.tencent.mm/...）：
 *   /MicroMsg/PluginPatch/
 *   /MicroMsg/HotPatch/
 *   /files/plugin/
 *   /tinker/
 */
public class FileWriteHook {

    static void hookFileOutputStream(ClassLoader cl) {
        try {
            // Hook FileOutputStream(File, boolean) - 最常用构造函数
            XposedHelpers.findAndHookConstructor(
                    FileOutputStream.class,
                    File.class,
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            File file = (File) param.args[0];
                            if (file != null && BlockedDomains.isBlockedFilePath(file.getAbsolutePath())) {
                                String msg = "[WxBlockHot] Blocked file write: " + file.getAbsolutePath();
                                Log.w(MainHook.TAG, msg);
                                XposedBridge.log(msg);
                                throw new FileNotFoundException(
                                        "WeChat hot-update file write blocked: " + file.getName()
                                );
                            }
                        }
                    }
            );

            // Hook FileOutputStream(File) - 无追加参数版本
            XposedHelpers.findAndHookConstructor(
                    FileOutputStream.class,
                    File.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            File file = (File) param.args[0];
                            if (file != null && BlockedDomains.isBlockedFilePath(file.getAbsolutePath())) {
                                String msg = "[WxBlockHot] Blocked file write(2): " + file.getAbsolutePath();
                                Log.w(MainHook.TAG, msg);
                                XposedBridge.log(msg);
                                throw new FileNotFoundException(
                                        "WeChat hot-update file write blocked: " + file.getName()
                                );
                            }
                        }
                    }
            );

            // Hook FileOutputStream(String) - 字符串路径版本
            XposedHelpers.findAndHookConstructor(
                    FileOutputStream.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String path = (String) param.args[0];
                            if (path != null && BlockedDomains.isBlockedFilePath(path)) {
                                String msg = "[WxBlockHot] Blocked file write(str): " + path;
                                Log.w(MainHook.TAG, msg);
                                XposedBridge.log(msg);
                                throw new FileNotFoundException(
                                        "WeChat hot-update file write blocked: " + path
                                );
                            }
                        }
                    }
            );

            XposedBridge.log("[WxBlockHot] Layer3 FileWriteHook 注入成功");

        } catch (Throwable e) {
            XposedBridge.log("[WxBlockHot] Layer3 FileWriteHook 失败: " + e.getMessage());
        }
    }
}
