# Block_zip（Minecraft 1.21.1 版）

把方块变种（半砖 / 楼梯 / 铜的氧化打蜡状态）收进一条**竖向折叠栏**的 Fabric 客户端模组。

> 这个目录是 **1.21.1** 版本（独立工程）。**26.3** 版本的完整文档见仓库根目录的 [README.md](../README.md)，
> 两版功能与操作完全一致。

## 快速开始

| 操作 | 效果 |
| --- | --- |
| 手持方块（没开界面）**按住 `` ` ``** | 在快捷栏上方竖向展开这个方块的全部变种 |
| 物品栏里鼠标指向方块，**按住 `` ` ``** | 在那一格旁边展开（鼠标不在格子上就什么都不展开） |
| 按住期间：**滚轮**（或 ↑↓） | 切换选中项，选中格套着原版快捷栏那圈加粗外框 |
| 按住期间：**鼠标点某一格** | 把变种贴到鼠标上（默认 1 个，按住 **Shift** 拿一整组） |
| **松开 `` ` ``** | 取出选中的变种并收起面板（没滚过就直接松开＝什么都不改） |

改键：`选项 → 控制 → 按键绑定 → Block_zip`。

## 环境要求

* **Minecraft 1.21.1**
* **Fabric Loader 0.16+**（实测 0.19.5）
* **Fabric API 0.116.17+1.21.1**
* **Java 21**

纯客户端模组：装在自己电脑上即可，**服务器不用装**。

## 与 26.3 版的技术差异

两版功能相同，但 API 差别很大，所以各用一套工具链与源码：

| | 26.3 版 | 1.21.1 版（本目录） |
| --- | --- | --- |
| 映射 | 官方已不混淆，无需映射 | **Mojang 官方映射**（`loom.officialMojangMappings()`） |
| 工具链 | Gradle **9.7.1** + Loom **1.18.2** + JDK **25** | Gradle **8.10.2** + Loom **1.7.4** + JDK **21** |
| 界面渲染 | `GuiGraphicsExtractor` + `Screen#extractRenderState` | `GuiGraphics` + `Screen#render` |
| 输入 | SDL 事件对象（`KeyEvent` / `MouseButtonEvent`） | **GLFW 整数**（keyCode / scanCode / button） |
| HUD | `HudElementRegistry` + `VanillaHudElements` | `HudRenderCallback` |
| 界面事件 | `ScreenEvents.afterForeground` | `ScreenEvents.afterRender` |
| 按键分类 | `KeyMapping.Category` 对象 | **字符串**（`key.categories.block_zip`） |
| 标识符 | `Identifier` | `ResourceLocation` |

保持一致的部分（踩过的坑也一并沿用）：

* 容器界面的**点击/滚轮/松开都用自定义 Mixin 拦在 `AbstractContainerScreen` 这一层**——
  Fabric 的 `ScreenMouseEvents` 在容器界面上语义不对（点击取消不掉子类处理、滚轮根本不触发）。
* 面板吃掉一次鼠标按下后，**紧跟着的那次松开也要吃掉**，否则原版会把物品放回格子或丢到世界。
* 取物**只用本地 `menu.setCarried`**，绝不发 `slotNum < 0` 的包（那等于 `player.drop`，会把物品丢进世界）。
* **长按要去重**：原版对每次 keydown（含系统自动重复）都 `KeyMapping.click()`，必须用上升沿触发。
* 创造物品栏的**分类栏/滚动条**在 `super` 之前就吃点击，面板盖住时要让它让位。

## 从源码构建

```bat
set JAVA_HOME=<你的 JDK 21 路径>
gradlew.bat build
```

产物：`build/libs/block_zip-1.0.0.jar`。

## 授权

MIT。源码与问题反馈：https://github.com/1741973626/block-zip
