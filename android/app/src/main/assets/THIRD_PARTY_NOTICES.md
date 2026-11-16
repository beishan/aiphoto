# 第三方组件声明

aiphoto 安卓端复用以下开源组件，版权归各项目贡献者所有。版本由 `app/build.gradle.kts` 锁定。

| 组件 | 来源 | 许可证 |
| --- | --- | --- |
| Kotlin | https://github.com/JetBrains/kotlin | Apache-2.0 |
| AndroidX：Compose、Activity、Lifecycle、Navigation、Room、Core、Media3 | https://android.googlesource.com/platform/frameworks/support/ | Apache-2.0 |
| OkHttp、Okio（间接依赖） | https://github.com/square/okhttp 、https://github.com/square/okio | Apache-2.0 |
| Gson | https://github.com/google/gson | Apache-2.0 |
| Coil | https://github.com/coil-kt/coil | Apache-2.0 |
| kotlinx.coroutines（间接依赖） | https://github.com/Kotlin/kotlinx.coroutines | Apache-2.0 |

测试组件 JUnit 4 为 EPL-1.0，AndroidX Test 为 Apache-2.0，MockWebServer 为 Apache-2.0，不随主 APK 分发。

Apache-2.0 完整许可随 APK 保存在 `assets/APACHE-2.0.txt`，本声明也随 APK 打包；第三方组件携带的元数据和 NOTICE 保留于依赖及构建资源中。这里的 UI 示例照片不作为客户端内置内容，运行时展示 NAS 真实数据。
