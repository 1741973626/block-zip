# IntelliJ IDEA 使用说明（Block_zip 工程专用）

本机已装：**IntelliJ IDEA Community 2025.2.6.2**
路径：`C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2`
已装插件：**Minecraft Development**（认 fabric.mod.json、Mixin、访问器）、**Ravel Remapper**（Yarn 名字批量换成 Mojang 名字）。

---

## 一、第一次打开

1. 启动：开始菜单搜 `IntelliJ IDEA Community`，或双击
   `C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.2.6.2\bin\idea64.exe`。
2. 首次会弹**用户协议** → 勾选同意 → Continue。
3. 如果问 "Import Settings（是否从 PyCharm 导入设置）" → 选 **Do Not Import Settings**（保持干净）。
4. 出现欢迎界面后点 **Open**（不是 New Project），选目录：

   ```
   C:\mods\Block_zip
   ```

5. 右下角会出现 **Gradle 同步进度条**，第一次要几分钟（在下载/索引 Minecraft 26.3）。
   同步完左侧会出现 `src/main`、`src/client` 两个源码目录。

> 同步报 Java 版本错怎么办？
> 本工程已经在 `gradle/gradle-daemon-jvm.properties` 里把 Gradle 守护进程锁成 **JDK 25**，
> 所以即使 IDEA 自带 Java 21 也能正常同步（已实测）。
> 万一还是提示找不到 JDK，就手动指一下：
> `File → Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM`
> 选 **Add JDK… → 选目录** `C:\mods\tools\jdk25\jdk-25.0.4.1+1`。

---

## 二、界面里最该认识的 4 个地方

| 位置 | 作用 |
|---|---|
| **左侧 Project 面板** | 看文件。本模组的代码全在 `src/client/java/com/blockzip/` |
| **右侧 Gradle 面板**（大象图标） | 双击任务运行，比如 `Tasks → fabric → runClient` 直接启动游戏 |
| **顶部右上角运行按钮**（▶ / 🐞） | 选 `runClient` 配置后，一键启动／调试游戏 |
| **底部 Run / Debug / Problems** | 看游戏日志、报错、编译错误 |

---

## 三、日常操作（快捷键记这几个就够）

| 想干的事 | 快捷键 |
|---|---|
| 找类（按名字） | `Ctrl + N`，输入 `VariantPanel` |
| 找文件 | `Ctrl + Shift + N` |
| 全文搜索 | `Ctrl + Shift + F`，例如搜 `KEY_GRAVE` |
| 搜任何东西（类/文件/设置/动作） | **连按两下 `Shift`** |
| 跳到定义 | `Ctrl + B`（或 `Ctrl + 左键`） |
| 回到刚才的位置 | `Ctrl + Alt + ←` |
| 看某个方法被谁调用 | `Alt + F7` |
| 自动修错 / 补 import | `Alt + Enter` |
| 格式化代码 | `Ctrl + Alt + L` |
| 重命名（全工程一起改） | `Shift + F6` |
| 撤销 / 重做 | `Ctrl + Z` / `Ctrl + Shift + Z` |
| 打开终端（就在 IDEA 里敲命令） | `Alt + F12` |

---

## 四、运行和调试游戏（开发模组最常用）

**方式 A：Gradle 面板（最稳）**
右侧 Gradle 面板 → `block_zip → Tasks → fabric → runClient` **双击** → 直接启动一个带本模组的 Minecraft 26.3。
旁边还有 `runServer`、`build`（打包）。

**方式 B：工具栏按钮（方便打断点）**
1. 右上角下拉框选 **runClient**（第一次没有就点 `Add Configuration → Gradle → 任务填 runClient`）。
2. 点 ▶ 运行，点 🐞 **Debug** 调试。
3. 在代码行号左边点一下打**红点断点**，游戏跑到那里就会停下来，能看变量、单步执行（`F8` 单步、`F9` 继续）。

> 改了代码**不用重新打包**：直接重启 runClient 就是最新代码。
> 想边玩边看日志：底部 Run 窗口就是游戏控制台，报错也会在这里。

---

## 五、这个工程的结构（要改功能就按这张表找）

```
src/main/                          通用侧（服务端也会加载，本模组这里是空的）
├── java/com/blockzip/BlockZip.java        空入口，只为让公共源集不为空
└── resources/fabric.mod.json              模组元数据（ID、版本、入口、依赖）
   resources/assets/block_zip/lang/*.json  中英文文本（按键名、提示语）

src/client/                        客户端侧（本模组的真正实现）
├── java/com/blockzip/client/
│   ├── BlockZipClient.java                入口：注册按键、挂界面事件、挂 HUD
│   ├── ui/VariantPanel.java               竖向面板：算位置、画背景/图标/名字、处理点击滚轮、取出变种
│   ├── variant/VariantIndex.java          变种归组规则（并查集：半砖/楼梯/氧化/打蜡）
│   ├── mixin/AbstractContainerScreenAccessor.java  只读原版 hoveredSlot / leftPos / topPos
│   └── debug/SelfTest.java                自检（设了环境变量才跑）
└── resources/block_zip.client.mixins.json  Mixin 配置
```

想改什么：**分组规则** → `VariantIndex.java`；**面板样子/位置/交互** → `VariantPanel.java`；**默认按键** → `BlockZipClient.java`。

---

## 六、打包成能玩的 jar

Gradle 面板 → `Tasks → build` 双击，或在 IDEA 的终端（`Alt+F12`）里敲：

```bat
gradlew.bat build
```

产物：`C:\mods\Block_zip\build\libs\block_zip-1.0.0.jar`。
把它丢进 `.minecraft\mods\`（同时要有 Fabric API）。

---

## 七、常见问题

| 现象 | 处理 |
|---|---|
| Gradle 同步失败、下载超时 | 工程 `gradle.properties` 里已经写了走本机 Clash 的 `127.0.0.1:7897` 代理，**先确认 Clash Verge 开着并且系统代理/TUN 是开的**；不用代理就把那 5 行 `systemProp.*` 注释掉 |
| 代码全是红的，`net.minecraft` 找不到 | 同步没跑完或失败：右侧 Gradle 面板点 **刷新（🔄）**；还不行就 `File → Invalidate Caches → Invalidate and Restart` |
| 提示 "Dependency requires at least JVM runtime version 25" | 见第一节末尾：把 Gradle JVM 指到 `C:\mods\tools\jdk25\jdk-25.0.4.1+1` |
| 提示找不到 fabric-loom / minecraft | 网络问题（同上），确认代理后再刷新 |
| runClient 启动后弹防火墙 | 点**允许**；整合服务器需要监听本机端口 |
| 想看中文界面 | 已装官方中文语言包（`localization-zh`），`Settings → Appearance & Behavior → System Settings → Language and Region` 选 Chinese |

---

## 八、Minecraft Development 插件能帮什么

- `fabric.mod.json` 里有**补全和校验**（写错 ID、依赖会有波浪线提示）。
- `@Mixin(SomeClass.class)` 里 `Ctrl+点击` 能跳到原版类；访问器/注入器有专门检查。
- `block_zip.client.mixins.json` 里写的 mixin 名如果找不到类，会直接标红。
- 写资源路径、物品/方块注册时有补全。

Ravel Remapper 是给**老模组从 Yarn 迁到 Mojang 官方映射**用的（26.1 起官方代码不再混淆）。
本工程是新写的，用不到；以后你想迁移自己旧模组时，可以从它的工具窗口里跑。
