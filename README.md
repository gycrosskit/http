# HTTP

新版 Maven 发布由 Jenkins 生成同版本归档、`source-receipt.json` 和 `SHA256SUMS`。JitPack 核对标签来源、归档尺寸和摘要后安装同一份制品。历史发布按原版本 Tag 复验。

提供 Android、iOS、OpenHarmony 共用的 HTTP 契约、Ktor 传输、平台引擎、上传及失败分类。`http-core` 不提供 UI 或 Kuikly Module；业务 URL、品牌、登录凭据、隐私同意、日志脱敏与请求策略由宿主负责。

当前正式版本 **1.1.0 已发布**，可从 JitPack 按下列固定坐标接入。公开产物核验及 Mac 独立消费者编译已通过，Linux 消费验证延后，完整平台验收尚未完成；宿主实际传输、接线和设备业务仍须分别验收。平台合同见[功能与平台差异](docs/功能与平台差异.md)，构建、测试和发布入口见[开发与验证](docs/开发与验证.md)。

## 架构与调用流程

宿主持有 Client、鉴权及请求策略；`AppHttpClient` 统一请求合同，`httpTransport` 连接 Android OkHttp、iOS Darwin 和 OHOS Curl。取消随调用协程传播，owner 结束时关闭 Client。

文件职责和调用关系见 [AOCI 索引](aoci.txt)，平台行为与重试边界见[功能与平台差异](docs/功能与平台差异.md)。

## 安装与最小使用

正式版本坐标：

```kotlin
repositories { maven("https://jitpack.io") }
commonMain.dependencies {
    implementation("com.github.gycrosskit.http:http-core:1.1.0")
}
```

保持宿主基线：Kotlin `2.2.21-1.0.0`、AGP `8.10.1`、Gradle `8.11.1`、Ktor `3.3.3-1.1.0-04`、coroutines `1.10.2-1.0.0`、serialization `1.9.1-1.0.0`。Android compileSdk 36 / minSdk 24 / JVM 11；iOS 生成 arm64、Simulator arm64/x64 KLIB，最低系统版本仍须随实际 Native 产物与宿主核验；OHOS 为 `ohosArm64`。无单独 SPM、Pod、HAR 或 ohpm 包。

Android 平台装配示例；iOS/OHOS 分别替换为 `createIosHttpClientEngine()` / `createOhosHttpClientEngine()`：

```kotlin
import com.dgtang.live.data.remote.*

val client = createHttpClient(createAndroidHttpClientEngine()).httpTransport(
    baseUrl = { "https://api.example.test" },
    interceptors = listOf(/* 宿主鉴权、隐私与日志策略 */),
)
try {
    val response = client.get("/catalog") {
        parameter("page", 1)
        enableIdempotentRetry(1) // 仅在服务端确认幂等时启用
    }
    check(response.status == 200)
} finally {
    client.close() // 由持有该 Client 的宿主 owner 收尾
}
```

`1.1.0` 保留三端引擎工厂原有无参入口，默认保留系统证书链和主机名验证；新增 `validateCertificates: Boolean` 显式重载。宿主确需兼容过期、自签名或无效证书时显式传 `false`，仅作用于该引擎，不改变全局 TLS、认证、重试和关闭语义。关闭后无法确认服务端身份，可能被中间人读取或篡改；后端完成证书治理后应统一恢复默认。变更见 [CHANGELOG](CHANGELOG.md)。

`expectSuccess=false`，HTTP 非 2xx 仍返回响应；不要把请求受理或网络成功当作业务成功。超时映射为 `HttpTransportException(TIMEOUT)`，外部取消继续抛出 `CancellationException`。默认零重试；显式重试只覆盖网络异常、超时与 5xx，不用于提交、支付、领取等非幂等请求。

公开工厂/扩展签名包含 Ktor 类型，因此 `ktor-client-core` 作为 `api` 依赖传递。Ktor 插件和各端引擎实现保留内部依赖。包名保持 `com.dgtang.live.data.remote`，便于宿主保持现有调用。

## 验证与反馈

回归、独立消费命令及各版本证据统一见[开发与验证](docs/开发与验证.md)；组件消费、宿主实际传输和设备验收分别记录。

发行入口：[1.1.0 Release](https://github.com/gycrosskit/http/releases/tag/1.1.0)。问题反馈请通过 [Issues](https://github.com/gycrosskit/http/issues) 提供脱敏输入、平台、精确版本及异常原因；不要上传凭据或用户正文。源码抽离自宿主 `c768cb12c86e59b60fbc69149013850edce509b0`，许可证为 [Apache-2.0](LICENSE)。
