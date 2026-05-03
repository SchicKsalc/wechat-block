# 保留所有 IXposedHookLoadPackage 实现类，防止混淆后 LSPosed 找不到入口
-keep class com.github.wechatblockhot.** { *; }

# 保留 XposedBridge 相关注解和接口
-keep class de.robv.android.xposed.** { *; }
-keepattributes *Annotation*
