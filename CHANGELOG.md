# 变更记录

## 1.1.0

- 保留 Android、iOS、OHOS 引擎工厂原有无参入口与证书验证行为，新增 `validateCertificates: Boolean` 显式重载。
- 宿主传入 `false` 时，该引擎接受无效服务端证书；Android/OHOS 同时关闭主机名验证，iOS 仅接管 ServerTrust challenge，其他认证方式保持系统默认。
- 增加本地自签名且主机名不符的 TLS 回归、原 JVM 无参方法兼容检查及三端独立消费者探针。

已发布并完成公开产物核验和 Mac 独立消费者编译；Linux 消费验证延后，完整平台与设备验收尚未完成。关闭验证的风险与恢复条件见 [README](README.md)。
