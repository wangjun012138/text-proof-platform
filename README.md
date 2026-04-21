# Text Proof Platform

文本/文件存证平台后端服务，基于 Spring Boot 构建。项目提供用户注册登录、验证码、密码管理、文本存证、文件存证、版本审计、文件下载以及按用户或匿名 token 分享等能力。

## 技术栈

- Java 17
- Spring Boot 3.4.12
- Spring Web
- Spring Security
- Spring Data JPA
- MySQL
- Lombok
- Bouncy Castle RFC 3161 时间戳相关组件
- JUnit 5 / Mockito / Spring Security Test

## 功能概览

- 用户认证：邮箱验证码、注册、登录、修改密码、重置密码。
- 登录安全：基于 Session 的登录态、单账号单会话、登录失败节流、CSRF 防护。
- 存证管理：创建文本存证、上传文件存证、查看列表和详情、更新、删除。
- 存证完整性：对文本或文件内容计算 SHA-256 哈希。
- 时间戳：支持关闭、LOCAL_DEMO、本地模拟时间戳，以及 FREETSA RFC 3161 服务。
- 版本审计：创建、更新、删除时保存审计快照。
- 分享访问：支持分享给指定用户，也支持生成匿名 token 分享链接。
- 文件下载：支持下载本人文件存证、用户分享文件、token 分享文件。

## 目录结构

```text
.
├── pom.xml
├── src
│   ├── main
│   │   ├── java/com/wangjun/text_proof_platform
│   │   │   ├── common              # 通用响应、异常处理
│   │   │   ├── config              # Security、密码编码等配置
│   │   │   └── modules
│   │   │       ├── user            # 用户、认证、验证码、登录节流
│   │   │       ├── proof           # 文本/文件存证、哈希、时间戳、审计
│   │   │       └── share           # 用户分享、token 分享
│   │   └── resources
│   │       └── application.yml
│   └── test
│       └── java/com/wangjun/text_proof_platform
└── runtime                         # 本地运行生成的文件、日志、上传存储目录
```

## 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.x

项目当前没有提交 Maven Wrapper，请使用本机 Maven 命令运行。

## 数据库准备

默认配置位于 `src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: "jdbc:mysql://localhost:3306/text_proof_db_learn?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&allowPublicKeyRetrieval=true"
    username: "root"
    password: "000000"
  jpa:
    hibernate:
      ddl-auto: update
```

本地启动前先创建数据库：

```sql
CREATE DATABASE text_proof_db_learn
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

如需修改数据库地址、账号或密码，请调整 `application.yml`，或在启动时用环境配置覆盖。

## 运行项目

安装依赖并启动：

```bash
mvn spring-boot:run
```

打包：

```bash
mvn -DskipTests package
```

运行 jar：

```bash
java -jar target/text-proof-platform-1.0-SNAPSHOT.jar
```

默认服务配置：

- 端口：`8080`
- 监听地址：`127.0.0.1`
- 文件存储目录：`./runtime/proof-files`
- RFC 3161 模式：`OFF`

注意：当前安全配置强制 HTTPS。直接访问 `http://127.0.0.1:8080` 会被重定向到 HTTPS。生产或联调环境建议使用 Nginx 等反向代理终止 HTTPS，并传递 `X-Forwarded-*` 请求头；项目已配置 `server.forward-headers-strategy=framework`。

## 时间戳配置

`proof.rfc3161.mode` 支持：

- `OFF`：不生成时间戳，默认值。
- `LOCAL_DEMO`：生成本地模拟时间戳，不依赖外部服务。
- `FREETSA`：调用 `https://freetsa.org/tsr` 生成 RFC 3161 时间戳。

示例：

```yaml
proof:
  rfc3161:
    mode: "LOCAL_DEMO"
    url: "https://freetsa.org/tsr"
    timeout-seconds: 15
```

## API 约定

统一 JSON 响应格式：

```json
{
  "code": 200,
  "message": "Query succeeded",
  "data": {}
}
```

常见错误码：

- `400`：请求参数错误或业务条件不满足。
- `401`：未登录或账号密码错误。
- `403`：无权限或 CSRF 校验失败。
- `404`：资源不存在。
- `429`：登录或验证码尝试次数过多，响应头包含 `Retry-After`。
- `500`：服务端内部错误。

除公开接口外，请求需要登录 Session。POST、PUT、DELETE 请求默认需要 CSRF token：

1. 先请求 `GET /api/auth/csrf` 获取 token。
2. 后续写请求携带返回的 header，例如 `X-XSRF-TOKEN: <token>`。
3. 同时保留服务端返回的 Cookie。

## 认证接口

| 方法 | 路径 | 说明 | 是否需要登录 |
| --- | --- | --- | --- |
| GET | `/api/auth/csrf` | 获取 CSRF token | 否 |
| POST | `/api/auth/code?email={email}` | 发送邮箱验证码，当前实现打印到服务端日志 | 否 |
| POST | `/api/auth/register` | 注册账号 | 否 |
| POST | `/api/auth/login` | 登录，成功后写入 Session | 否 |
| POST | `/api/auth/password/change` | 修改当前登录用户密码 | 是 |
| POST | `/api/auth/password/reset` | 通过邮箱验证码重置密码 | 否 |

注册请求示例：

```json
{
  "email": "alice@example.com",
  "username": "alice",
  "password": "Alice123",
  "code": "123456"
}
```

登录请求示例：

```json
{
  "account": "alice",
  "password": "Alice123"
}
```

密码规则：长度 6-50，必须同时包含字母和数字。

## 存证接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/proof/text` | 创建文本存证 |
| POST | `/api/proof/file` | 创建文件存证，`multipart/form-data` |
| GET | `/api/proof/list` | 查看当前用户存证列表 |
| GET | `/api/proof/{id}` | 查看存证详情 |
| GET | `/api/proof/{id}/download` | 下载文件存证 |
| PUT | `/api/proof/{id}/text` | 更新为文本存证 |
| PUT | `/api/proof/{id}/file` | 更新为文件存证 |
| DELETE | `/api/proof/{id}` | 删除存证 |
| GET | `/api/proof/{id}/history` | 查看存证历史版本 |

创建文本存证示例：

```json
{
  "subject": "合同摘要",
  "content": "这里是需要存证的文本内容"
}
```

创建文件存证使用表单字段：

- `subject`：存证主题。
- `file`：上传文件。

存证内容会记录：

- `contentType`：`TEXT` 或 `FILE`
- `contentHash`：内容 SHA-256
- `versionNo`：版本号
- `rfc3161Status`、`rfc3161Provider`、`rfc3161TimestampAt`：时间戳状态

## 分享接口

| 方法 | 路径 | 说明 | 是否需要登录 |
| --- | --- | --- | --- |
| POST | `/api/share/user` | 分享给指定用户 | 是 |
| POST | `/api/share/token` | 生成匿名 token 分享 | 是 |
| GET | `/api/share/my/list` | 查看我发出的分享 | 是 |
| POST | `/api/share/{id}/revoke` | 撤销分享 | 是 |
| GET | `/api/share/user/{id}` | 指定用户查看分享内容 | 是 |
| GET | `/api/share/user/{id}/download` | 指定用户下载分享文件 | 是 |
| GET | `/api/share/token/{token}` | 匿名 token 查看分享内容 | 否 |
| GET | `/api/share/token/{token}/download` | 匿名 token 下载分享文件 | 否 |

分享给用户示例：

```json
{
  "textProofId": 1,
  "targetUsername": "bob",
  "expireDays": 7
}
```

生成 token 分享示例：

```json
{
  "textProofId": 1,
  "expireDays": 7
}
```

分享会校验过期时间和撤销状态。按用户分享还会校验当前登录用户是否等于 `targetUsername`。

## 安全说明

- 使用 Spring Security Session 保存登录态。
- 登录成功后会更换 Session ID，降低会话固定风险。
- 同一账号只保留最新登录会话，旧会话会过期。
- 写请求启用 CSRF 防护。
- Session Cookie 默认 `Secure`、`HttpOnly`、`SameSite=Lax`。
- 默认强制 HTTPS，并启用 HSTS。
- 匿名访问只开放 token 分享查询/下载和认证相关公开接口。

## 测试

运行全部测试：

```bash
mvn test
```

运行指定测试：

```bash
mvn -Dtest=AuthControllerTest test
mvn -Dtest=TextProofServiceTest test
mvn -Dtest=TextProofShareServiceTest test
```

当前测试覆盖重点包括：

- CSRF 获取和缺失 CSRF 时的 403 JSON 响应。
- HTTP 到 HTTPS 重定向。
- 登录成功、登录失败、登录节流。
- 请求参数校验和坏 JSON 处理。
- 文件存证失败时清理已写入文件。
- 存证创建时保存审计快照。
- 用户分享、token 分享、过期/撤销校验和分享文件下载。

## 开发注意事项

- `runtime/` 下的文件是运行时产生的上传文件、Cookie、日志或回归验证文件，通常不应作为业务代码提交。
- `application.yml` 中包含本地数据库账号密码，生产环境应改用环境变量、配置中心或部署系统注入。
- 当前验证码发送逻辑只保存验证码并打印到控制台，尚未接入真实邮件服务。
- 使用 `FREETSA` 模式需要服务端能够访问外部 TSA 服务；外部网络异常时存证不会中断，但时间戳状态会记录为失败。
