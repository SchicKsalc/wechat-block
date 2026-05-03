# 微信热更新屏蔽 · LSPosed 模块

## 模块架构

```
5 层防护链：
 ┌────────────────────────────────────────────────────────┐
 │  Layer 1  java.net.URL.openConnection()                 │
 │           → 在 TCP 连接建立前拦截热更新域名的请求         │
 ├────────────────────────────────────────────────────────┤
 │  Layer 2  OkHttp RealCall.execute()                     │
 │           → Hook 微信内嵌 OkHttp，过滤更新 URL           │
 ├────────────────────────────────────────────────────────┤
 │  Layer 3  FileOutputStream 构造函数                      │
 │           → 阻止热补丁文件写入磁盘                        │
 ├────────────────────────────────────────────────────────┤
 │  Layer 4  DexClassLoader 构造函数                        │
 │           → 阻止加载已下载的热补丁 DEX 文件               │
 ├────────────────────────────────────────────────────────┤
 │  Layer 5  热更新调度类 + SharedPreferences 标志           │
 │           → 从逻辑层禁用热更新任务调度                    │
 └────────────────────────────────────────────────────────┘
```

## 拦截域名列表

| 域名 | 功能 |
|------|------|
| `upgrade.weixin.qq.com` | 版本升级检查 |
| `szextshort.weixin.qq.com` | 热补丁分发 |
| `short.weixin.qq.com` 系列 | 短连接补丁下载 |
| `dldir1.qq.com` | 腾讯下载 CDN |
| `res.wx.qq.com` | 功能模块分发 |
| `appconfig.weixin.qq.com` | 热更新配置获取 |

## 构建方法

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更新版本
- JDK 11+
- Android SDK 34

### 步骤

```bash
# 1. 克隆/解压项目
cd WeChatBlockHotUpdate

# 2. 构建 release APK
./gradlew assembleRelease

# 3. 输出文件路径
app/build/outputs/apk/release/app-release.apk
```

> 如果没有签名配置，Gradle 会生成 debug APK：`./gradlew assembleDebug`

### 安装

1. 将 APK 安装到手机（或通过 ADB）
2. 打开 **LSPosed Manager**
3. 在「模块」列表中找到「微信热更新屏蔽」并启用
4. 在「作用域」中勾选 **微信（com.tencent.mm）**
5. 强制停止微信后重新启动，Hook 即生效

## 验证方法

Hook 生效后，可通过以下方式验证：

```bash
# 查看 XposedBridge 日志
adb shell logcat -s Xposed:V | grep WxBlockHot
```

输出示例：
```
[WxBlockHot] 微信进程已加载，开始注入 Hook...
[WxBlockHot] Layer1 NetworkHook (URL) 注入成功
[WxBlockHot] Layer2 OkHttpHook 注入成功
[WxBlockHot] Layer3 FileWriteHook 注入成功
[WxBlockHot] Layer4 DexLoadHook 注入成功
[WxBlockHot] Layer5 ReceiverHook 注入完成
[WxBlockHot] 所有 Hook 注入完成 ✓
```

拦截日志示例：
```
[WxBlockHot] Blocked URL: szextshort.weixin.qq.com
[WxBlockHot] Blocked file write: /MicroMsg/HotPatch/patch_xxx.zip
```

## 兼容性

| 条件 | 状态 |
|------|------|
| Android 8.1 ~ 14 | ✅ 支持 |
| LSPosed 1.8.x+ | ✅ 支持 |
| 微信 8.x | ✅ 已适配 |
| 微信使用 VPN/代理 | ✅ Layer 3/4 仍有效 |

## 注意事项

- 本模块**不影响**微信的正常聊天、支付、小程序等功能
- 如微信出现异常，可在 LSPosed 中临时关闭模块排查
- 微信大版本更新（通过应用商店）不受影响，仅屏蔽静默热补丁
