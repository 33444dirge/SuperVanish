# SuperVanish 构建

当前版本及上游对齐范围见 [UPSTREAM-JIUJIU.md](UPSTREAM-JIUJIU.md)。维护源码位于 `jiujiu/src/main`；根 `src/main` 保留上游源码，Gradle 从中直接编译三种共用事件。首次运行需要下载 Gradle 和测试依赖。

使用 JDK 25，在当前目录执行：

```bat
gradlew.bat clean build
```

Linux/macOS 使用 `./gradlew` 运行相同任务。若依赖已缓存，可增加 `--offline`。

成品目录：`build/libs/`。`*-sources.jar` 为源码包；依赖目录中的 JAR 仅供编译，不是本插件成品。

当前项目名、包名、权限节点、配置引用及编译接口已同步统一命名。旧部署的插件数据目录、权限配置及外部资源引用需按当前名称同步迁移；本轮没有连接或修改服务器。
