# orbit-workbench-java

Orbit Workbench 的 Spring Boot 后端，目标运行时为 JDK 21 和 MySQL 8。

## 必需环境变量

```text
ORBIT_DB_URL
ORBIT_DB_USERNAME
ORBIT_DB_PASSWORD
ORBIT_MASTER_KEY
ORBIT_STORAGE_ROOT
```

`ORBIT_MASTER_KEY` 必须是 Base64 编码的 32 字节随机密钥。项目不会读取仓库外的临时 API Key 文件。

## 静态验证

```powershell
mvn test
mvn verify
```

运行和第三方模型联调需在 JDK 21、本地 MySQL 以及上述环境变量准备完成后进行。

