# 编译依赖与来源

以下 JAR 均为用户原归档随附的编译输入，保留原文件内容，没有打入插件。

目标服务端为 Mizuki/Shiroha 26.2 f10fd16、Java 25。`mizuki-26.2-compile-signatures.jar` 为从目标核心提取的签名，`coreprotect-24.0-prelog-compile-signatures.jar` 为 CoreProtect 24.0 预记录事件签名；方法体为占位实现，不能安装运行。提取工具为 `tools/CompileSignatures.java`，需用户自备同版本原 JAR 重新提取。

原始 CoreProtect 输入 SHA256：`0983D1766DD08799ED62FAB3C77F4086A3599506378A17DB4132711051F0A1B7`。其余原始服务端及 API 没有提供可重新下载的固定地址，因此本分支保留归档输入和校验值，不宣称从公共 Maven 重新生成这些签名。

新增测试依赖由 Maven Central 获取：JUnit 5.13.4、Mockito 5.21.0、SnakeYAML 2.6、Adventure 5.2.0、SLF4J 2.0.17、Log4j API 2.26.0、Gson 2.14.0、JOML 1.10.8。仅测试使用，不打入成品。

| 文件 | 字节数 | SHA256 |
| --- | ---: | --- |
| `adventure-api-5.2.0.jar` | 428448 | `7e52fe7190be3e87b3b3f71712cfa12315fcd27109f302a7b440c12f01fae827` |
| `adventure-key-5.2.0.jar` | 11211 | `0184d173200e2eef8fbc791f622d1d58fd459f8930c616b5a4fe79e83eda6c55` |
| `adventure-text-serializer-legacy-5.2.0.jar` | 30336 | `1231a0de417de66c59e2f2a3892ecca6b8d48621df6f1d9a2f81b01f57e328b7` |
| `annotations-26.1.0.jar` | 31094 | `ebc7aec252ed0c7d2d04c039d7f00e69f7b86b1f493c741d67b3ef31b986b054` |
| `asm-9.9.1.jar` | 126252 | `6f3828a215c920059a5efa2fb55c233d6c54ec5cadca99ce1b1bdd10077c7ddd` |
| `bungeecord-chat-1.21-R0.2-deprecated+build.21.jar` | 168064 | `ce930b3ee048520a0ab538eb8d1627b301367fc1b300837235387aded6d89ba1` |
| `coreprotect-24.0-prelog-compile-signatures.jar` | 1789 | `64e6ce5fb1bd83edc26a43dc958e7228826e4b6b28b714fae7be170d74d13876` |
| `fastutil-8.5.18.jar` | 23965563 | `9094ae67d01d0ad246f886f11ad557fc2e79c72cbf3feed83e1512a8ae90a74a` |
| `guava-33.6.0-jre.jar` | 3064793 | `dc573e1fca4fd5454f4a5fd3d7da2df03002876a4175bafc14a95980dd7713b3` |
| `mizuki-26.2-compile-signatures.jar` | 8923446 | `f0bdbedc7daad06e9a4a8b447f02e37d08d79f27529f8c63f85b9156fd97807e` |
| `shiroha-api-26.2-SNAPSHOT.jar` | 2905113 | `b0c274c9077f24449d91078cd7008055689ae58788695a600ea9f634672bcb3d` |
