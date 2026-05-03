package com.github.wechatblockhot;

import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 微信热更新屏蔽 - LSPosed 模块入口
 *
 * 拦截策略（多层防护）：
 *  Layer 1 - NetworkHook   : 拦截 java.net.URL.openConnection，屏蔽热更新域名的网络请求
 *  Layer 2 - OkHttpHook    : Hook WeChat 内嵌的 OkHttp，过滤更新相关 URL
 *  Layer 3 - FileWriteHook : 拦截 FileOutputStream，阻止热补丁文件写入磁盘
 *  Layer 4 - DexLoadHook   : Hook DexClassLoader，阻止加载已存在的热补丁 DEX
 *  Layer 5 - ReceiverHook  : 静默关闭微信内部热更新广播接收器
 */
public class MainHook implements IXposedHookLoadPackage {

    static final String TAG = "WxBlockHot";
    static final String WECHAT_PKG = "com.tencent.mm";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        // 仅对微信进程生效
        if (!WECHAT_PKG.equals(lpparam.packageName)) return;

        XposedBridge.log("[WxBlockHot] 微信进程已加载，开始注入 Hook...");
        Log.i(TAG, "WeChat process loaded, injecting hooks...");

        ClassLoader cl = lpparam.classLoader;

        // Layer 1: 标准 URL 网络层拦截
        NetworkHook.hookUrlOpenConnection(cl);

        // Layer 2: 微信内嵌 OkHttp 拦截（支持 com.tencent.mars 和 bundled okhttp3）
        OkHttpHook.hookOkHttp(cl);

        // Layer 3: 文件写入拦截（阻止补丁写入磁盘）
        FileWriteHook.hookFileOutputStream(cl);

        // Layer 4: DEX 动态加载拦截（阻止加载已下载的热补丁 DEX）
        DexLoadHook.hookDexClassLoader(cl);

        // Layer 5: 微信热更新广播/Service 拦截
        ReceiverHook.hookUpdateReceiver(cl);

        XposedBridge.log("[WxBlockHot] 所有 Hook 注入完成 ✓");
    }
}
