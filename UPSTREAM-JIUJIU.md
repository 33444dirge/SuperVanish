# SuperVanish 上游与 JiuJiu 维护分支

## 基线与范围

远端为 [33444dirge/SuperVanish](https://github.com/33444dirge/SuperVanish)，默认分支 `master`，本次核对的最新提交为 `a1d6093569062afdac72daec45430a94a3065bab`，版本 **6.3.0**（2026-04-26）。`jiujiu` 从该提交建立，保留完整 Git 历史、上游 `src/main`、Maven 文件及许可证；不修改或推送主分支。

本地输入没有 Git 历史，是另一主类和数据结构的 Folia 维护源码，不是可直接 merge 的原作者分支；因此无法计算“本地缺少多少提交”。本次做法是保留最新上游源码作为比较基线，将本地源码置于 `jiujiu/src/main/java`、资源置于 `jiujiu/src/main/resources`，按适用范围接入上游行为。**运行插件仍为 Folia 维护实现，不是把上游所有功能整体移植到了 Folia。**

版本从 `26.x_3.4-jiujiu-cjmfix2-stealth3` 升为 `26.x_3.4-jiujiu-cjmfix2-stealth4`。插件名 `SuperVanish`、主类 `dev.philippcmd.supervanish.Main`、既有命令/权限、`players.yml` 的 `vanished` / `super-vanished` 字段、`previous_gamemode` PDC 和 `stealth.yml` 保持兼容。

## 上游更新与接入情况

| 上游范围 | 上游变化 | 本分支处理 |
| --- | --- | --- |
| 6.3.0 / `a1d6093` | Minecraft 26.1 版本识别、移除 Lombok、修复构建 | 保留完整源码；维护版直接使用已核对的 26.2 API/NMS 签名，无 Lombok 或旧版本字符串判断，继续使用 Java 25 + Gradle |
| 6.2.21 / `84627af` | 改进 hide/show API、移除新版服务端每 tick 重复隐藏 | 本地已有 `hidePlayer(plugin, target)` / `showPlayer(plugin, target)`，按观察者所属区域执行，无重复隐藏轮询，保留此路径 |
| 6.2.21 / `84627af` | 加入玩家时优先隐藏其他隐身玩家 | 本地加入时已双向更新观察者与目标关系，保留并回归验证普通/超级隐身可见规则 |
| 标准事件 API | 取消式显隐事件、状态事件、完成事件 | 直接编译上游 `PlayerVanishStateChangeEvent`、`PostPlayerHideEvent`、`PostPlayerShowEvent` 并接入实际状态切换；原 Hide/Show 兼容类去掉 final，保留本地单参数构造 |
| TabCompleteListener | 隐藏无权看见的玩家名字，忽略控制台 | 已移植；使用名字/UUID 快照，避免跨区域读取玩家对象；复制不可修改的补全列表再设置结果 |
| OpenInv、ProtocolLib、PAPI、Dynmap 等原版集成 | 上游另一套主类、配置、权限和插件生命周期 | 源码完整保留，但未编译进入维护插件，不声称已支持；静默铜箱继续采用本地原生 26.2 实现 |

新增事件顺序为：`PlayerHideEvent/PlayerShowEvent` → `PlayerVanishStateChangeEvent` → 更新状态与元数据、派发可见性任务 → 原 CJM `VanishStateChangeEvent` → `PostPlayerHideEvent/PostPlayerShowEvent`。任一前置事件取消时不修改状态、不发布完成事件；普通与超级隐身互换不重复发布显隐边界事件。Post 事件表示本地状态更新并已派发跨区域任务，不保证所有客户端在事件当刻已收到网络包。兼容监听器修改的 silent 值传递到 Post 事件；维护插件自身继续使用既有中文提示策略。

## 保留的本地功能

- 普通隐身、超级隐身、指定观察者显示，保留 CJM 的事件和 `getVanishManager()` 接口。
- Folia 玩家/区域归属检查和任务关闭清理，`players.yml` 原子保存。
- 双击潜行切换旁观与原游戏模式恢复。
- 隐身时禁止物品/经验拾取；普通箱、陷阱箱和八种铜箱原生静默开关，保留真实共享库存及异步只读快照。
- CoreProtect 24.0 现有预记录事件过滤，TAB 使用的本插件 `vanished` 元数据和中文巡查配置。

不扩展原 CoreProtect 覆盖范围、不覆盖其配置和数据库；记录过滤仍以回调时的隐身状态为准。上游完整 `VanishAPI` 依赖上游主类，未加入维护产物；本分支兼容面为上述五种事件及原 CJM 接口。

## 构建

安装 JDK 25，在仓库根目录运行：

```bat
gradlew.bat clean build --console=plain
```

Linux/macOS 使用 `bash ./gradlew clean build --console=plain`。首次需要下载 Gradle 与测试依赖，缓存完整后可用 `--offline`。不需要 PowerShell 构建脚本。

默认 Gradle 入口只编译维护源码和三个上游事件定义，使用维护资源；上游 `pom.xml` 留作比较，不用于本次 Folia 成品构建。输入签名与来源见 [dependencies/SOURCES.md](dependencies/SOURCES.md)。

成品：`build/libs/SuperVanish-26.x_3.4-jiujiu-cjmfix2-stealth4.jar`（52,111 字节）。

SHA256：`5fc9a10c81a6f72aeeac0cae06562833391eab2bd715d31e4a5e5eebec0872fd`。

## 验证

- Gradle 9.7.1 / Java 25 clean build 通过，14 项新增自动测试全部通过，无失败、错误或跳过。
- 覆盖前后事件顺序、两类前置取消、显示取消保留状态、模式互换、重入阻止、silent 传递、线程归属、普通授权/超级隐身补全过滤、控制台与退出、元数据、原资料格式与八种铜箱范围。
- 成品主类、版本、Folia 标志、Java 25 字节码、Mojang 映射标志、五个兼容事件和许可证通过核对；没有服务端、CoreProtect、编译签名、测试依赖或上游另一套运行主类泄漏。
- 本次没有启动实际服务器或真人客户端；原文档中 stealth2/3 的测试记录属于此前版本，不能计入本次验证。

持续集成仅针对 `jiujiu` 构建并上传插件与测试报告，不发布 release。后续先 `git fetch upstream` 比较 `upstream/master`，核对适用更新并回归后，仅推送 `HEAD:refs/heads/jiujiu`。
