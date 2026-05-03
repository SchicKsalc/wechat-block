package com.github.wechatblockhot;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 需要屏蔽的域名列表（集中管理，便于维护）
 */
public class BlockedDomains {

    /** 精确域名匹配（toLowerCase 后比较） */
    private static final Set<String> EXACT = new HashSet<>(Arrays.asList(
            // ---- 版本升级检查 ----
            "upgrade.weixin.qq.com",
            "updateapp.weixin.qq.com",

            // ---- 补丁/热更新分发（short 系列） ----
            "short.weixin.qq.com",
            "szextshort.weixin.qq.com",
            "szshort.weixin.qq.com",
            "sgshort.weixin.qq.com",
            "hkshort.weixin.qq.com",
            "minorshort.weixin.qq.com",
            "szextshort2.weixin.qq.com",

            // ---- 功能模块/插件 CDN ----
            "res.wx.qq.com",
            "res2.wx.qq.com",

            // ---- 腾讯下载服务 ----
            "dldir1.qq.com",
            "dldir1v6.qq.com",

            // ---- 热更新配置下载 ----
            "appconfig.weixin.qq.com"
    ));

    /** 路径关键词匹配（URL 路径中包含即拦截）*/
    private static final String[] PATH_KEYWORDS = {
            "/hotpatch",
            "/plugin/",
            "/appupdate",
            "/upgrade",
            "/tinker",          // Tinker 热修复框架路径
            "/robust",          // Robust 热修复框架路径
            "/patch.zip",
            "/patch.apk",
            "/_patch_",
    };

    /** 热更新文件写入路径关键词（FileOutputStream 拦截用）*/
    static final String[] BLOCKED_FILE_PATHS = {
            "/MicroMsg/PluginPatch",
            "/MicroMsg/HotPatch",
            "/MicroMsg/appbrand/update",
            "/files/plugin",
            "/tinker/",
            "/_patch_",
            "/robust/",
            "/hotpatch",
    };

    /**
     * 判断给定 host 是否属于需要屏蔽的域名
     */
    public static boolean isBlockedHost(String host) {
        if (host == null || host.isEmpty()) return false;
        String lower = host.toLowerCase();
        return EXACT.contains(lower);
    }

    /**
     * 判断完整 URL 是否属于热更新请求（域名 + 路径双重判断）
     */
    public static boolean isBlockedUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();

        // 提取 host 部分做域名匹配
        try {
            java.net.URL u = new java.net.URL(url);
            if (isBlockedHost(u.getHost())) return true;
        } catch (Exception ignored) {}

        // 路径关键词匹配
        for (String kw : PATH_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    /**
     * 判断文件路径是否属于热更新写入目录
     */
    public static boolean isBlockedFilePath(String path) {
        if (path == null || path.isEmpty()) return false;
        String lower = path.toLowerCase();
        for (String kw : BLOCKED_FILE_PATHS) {
            if (lower.contains(kw.toLowerCase())) return true;
        }
        return false;
    }
}
