# 开发与自测指引

这份文档写给接手开发的 AI agent。重点不是「怎么写代码」，而是**改完之后如何自己验证**，
因为这个项目里绝大多数错误不会让编译失败，也不会在日志里报错——它们只会在游戏里安静地
表现错误。你必须主动去查，不能等工具报错。

---

## 0. 一句话工作流

每次改完代码，按顺序跑这三步，全绿才算完成：

```bash
./gradlew build --offline -q     # 1. 编译（只查 Java）
python3 scripts/verify.py        # 2. 静态校验（查编译查不到的东西）
# 3. 若改了配方/战利品表/标签/注册逻辑，再跑一次服务端，见第 4 节
```

**不要在没跑完这三步之前声称改动完成。**

---

## 1. 环境事实

这些是实测确认过的，不要凭印象假设。

| 项目 | 值 |
|---|---|
| Minecraft | 1.20.1 |
| 模组加载器 | Fabric（Loom 1.9.2） |
| 映射 | Yarn `1.20.1+build.10` |
| **Java 语言级别** | **17**（`build.gradle` 里 `options.release = 17` + toolchain 17） |
| 系统 JDK | 21（但**不能**用 Java 18+ 的 API，见下） |
| Gradle | 8.12.1 |
| mod id / 命名空间 | `keyboard_workstations` |
| Mixin | **没有**，本项目不使用 mixin |
| MCA 兼容 | 纯反射，无编译期依赖（见 `McaVillagers.java`） |

**Java 17 的坑**：系统装的是 JDK 21，你写 `Math.clamp(...)` 时 IDE 可能不报错，但编译会失败，
因为它是 Java 21 才有的。用 `MathHelper.clamp(...)`。凡是 Java 18 及以后新增的标准库 API 都不能用。

---

## 2. 编译

```bash
./gradlew build --offline -q
```

`--offline` 是快路径，约 5 秒。

### 已知故障：离线缓存失效

如果报这个错：

```
Plugin [id: 'fabric-loom', version: '1.9.2'] was not found in any of the following sources
```

**这不是你的代码坏了**。Gradle 的插件解析缓存失效了，通常发生在改了 `settings.gradle` 里的
`rootProject.name`、或者 Gradle 发行版被重新下载之后。解决办法是去掉 `--offline` 重跑一次：

```bash
./gradlew build            # 需要联网，首次可能要 3-5 分钟
```

联网成功一次之后，`--offline` 又能用了。

### build 能查出什么、查不出什么

| build 能查出 | build **查不出** |
|---|---|
| Java 语法与类型错误 | JSON 语法错误（比如多余的逗号） |
| 方法/字段名写错 | 模型指向了不存在的贴图 |
| 缺少 import | 语言键没翻译 |
| | 配方引用了没注册的物品 |
| | 任何运行时行为 |

右边那一整列就是 `scripts/verify.py` 存在的理由。

---

## 3. 静态校验（最重要的一步）

```bash
python3 scripts/verify.py
```

退出码 0 表示通过，1 表示失败并会列出每一项问题。它检查七件事：

1. **所有 JSON 能解析** —— 多一个逗号，游戏会直接丢弃该文件，只留一行没人看的日志。
2. **mod id 四处一致** —— `fabric.mod.json`、`WorkstationsMod.MOD_ID`、`assets/` 目录名、
   `data/` 目录名。这四个是彼此独立的字符串，改名时漏掉任何一个，模组都会用一个 id 注册东西
   却去另一个名字下找资源。
3. **中英文语言文件键集合完全相同** —— 只加了 `en_us` 忘了 `zh_cn`，用英文测试的人永远发现不了。
4. **代码引用的每个语言键都有翻译** —— 键有三种到达语言文件的方式（写死的字面量、由
   `SettingOption` 名字在运行时拼出来的、原版从注册名推导出来的），三种都会以同样的方式静默失败，
   所以三种都查。
5. **模型/方块状态引用的贴图和模型文件真实存在** —— 贴图缺失渲染成黑紫格，日志里一个字都没有，
   很容易被误判成 UV 画错了。
6. **数据文件里的注册名确实被注册过** —— 配方、战利品表、标签里写的是**注册名**不是文件路径，
   打错字的结果不是文件缺失，而是配方被静默丢弃。
7. **没有失效的 import** —— javac 对此一言不发。

> **改了资源文件或任何长得像语言键的字符串字面量，就必须跑这个脚本。**

---

## 4. 跑服务端（验证注册与数据包）

```bash
# 只需要做一次：
printf 'eula=true\n' > run/eula.txt
```

然后：

```bash
timeout -s INT 300 ./gradlew runServer 2>&1 | tail -60
```

### 三个必须知道的操作要点

1. **服务端永远不会自己退出。** 它启动完成后会一直挂着等指令。如果你不加 `timeout` 就直接等它
   结束，看起来就像卡死了——实际上它运行得好好的。**必须**用 `timeout` 或者启动后主动 kill。

2. **成功的标志是日志里出现 `Done (`**，例如 `Done (15.500s)! For help, type "help"`。
   看到这行就说明模组注册、数据包加载全部通过了。

3. 结束后完整日志在 `run/logs/latest.log`，比终端输出更好查。

### 它能查出什么

- 注册阶段的崩溃（重复 id、注册顺序错误等）
- 配方、战利品表、标签的解析错误
- 模组加载失败、依赖缺失

### 它**查不出**什么

**服务端不加载任何客户端资源。** 模型、贴图、语言文件、界面代码它一概不碰。这些只能靠
`scripts/verify.py` 静态检查，加上人工在客户端里看。

### 日志解读的两个陷阱

**不要看 `Loaded N recipes` 这个数字。** 这个开发环境里它恒定显示 `Loaded 7 recipes`，
改动前的每一份历史日志都是 7，属于环境特性而非问题。要判断配方是否正常，请搜索错误行：

```bash
grep -iE "parsing error|couldn't parse|failed to load" run/logs/latest.log
```

**忽略这些无害的噪音：**

- `No data fixer registered for mca:...` —— MCA 的，与本项目无关
- `Failed to load properties from file: server.properties` —— 首次启动没有该文件，会自动生成
- `Cannot remap m_xxxxx_ ...` —— Architectury 通用包的重映射警告

`run/mods/` 里装着 Fabric API、Architectury 和 MCA，所以日志里出现它们是正常的。

---

## 5. 跑客户端

```bash
./gradlew runClient
```

需要图形界面。如果你没有显示器，**不要假装你验证过渲染效果**。渲染、动画、界面布局、
NPC 长时间的 AI 行为，这些只能由人来看。诚实地告诉用户「这部分需要你进游戏确认」，
并说清楚具体要看什么。

---

## 6. 查原版 API：不要凭记忆猜 Yarn 名字

这是本项目最有价值的一条技巧。Yarn 映射的名字经常和你的直觉不一样，**猜错会浪费一整轮编译**。

本仓库没有反编译好的源码，但有映射好的 Minecraft jar。这样找到它（路径里的哈希会变，所以用
`find` 而不要写死）：

```bash
MC=$(find .gradle/loom-cache/minecraftMaven -name 'minecraft-merged-*.jar' | head -1)
```

然后用 `javap` 查真实的类结构：

```bash
javap -cp "$MC" net.minecraft.block.BlockRenderType          # 看枚举常量
javap -cp "$MC" net.minecraft.block.entity.ChestBlockEntity  # 看方法签名
javap -p -cp "$MC" net.minecraft.entity.LivingEntity | grep -i push   # 找特定方法
```

### 真实踩坑记录

| 凭直觉写的 | 实际正确的 | 怎么发现的 |
|---|---|---|
| `BlockRenderType.ENTITYBLOCK_ANIMATION` | `ENTITYBLOCK_ANIMATED` | 编译失败后 javap 枚举 |
| `ChestAnimationProgress` 接口 | `LidOpenable` | javap 报「class not found」 |
| `Math.clamp` | `MathHelper.clamp` | Java 17 没有前者 |

### 查类里的字符串常量

模型部件名之类的字符串可以从 class 文件里直接捞出来，但**注意 `strings` 默认只输出 4 字符以上**，
这会漏掉短名字（找箱子模型部件时 `bottom` 和 `lock` 出来了，`lid` 却没有）：

```bash
unzip -p "$MC" net/minecraft/client/render/block/entity/ChestBlockEntityRenderer.class \
  | strings -n 3 | grep -xE "lid|bottom|lock"
```

### 抄原版的资源文件

原版的模型、方块状态、语言文件都在客户端 jar 里，直接抄比自己编靠谱：

```bash
CJ=~/.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar
unzip -p "$CJ" assets/minecraft/models/item/chest.json
unzip -p "$CJ" assets/minecraft/blockstates/chest.json
```

### 需要完整源码时

```bash
./gradlew genSourcesWithVineflower    # 慢，几分钟，但能读到真正的原版实现
```

`javap` 够用就别跑这个。

---

## 7. 按改动类型决定要跑什么

| 你改了什么 | build | verify.py | runServer | 需要人工确认 |
|---|---|---|---|---|
| 纯 Java 逻辑 | 必须 | 建议 | 建议 | 行为是否符合预期 |
| 新增方块 / 物品 / 实体 | 必须 | **必须** | **必须** | 外观 |
| 配方 / 战利品表 / 标签 | — | **必须** | **必须** | — |
| 模型 / 贴图 / 方块状态 | — | **必须** | — | **必须**，只能人眼看 |
| 语言文件或任何界面文字 | — | **必须** | — | 建议 |
| 改 mod id 或命名空间 | 必须 | **必须** | **必须** | 建议 |
| AI / 行为逻辑 | 必须 | — | 建议 | **必须**，需要观察一段时间 |

---

## 8. 代码风格约定

改代码时请沿用现有风格，不要另起一套。

- **缩进用 Tab**，Java 和 JSON 都是。
- **注释解释「为什么」，不解释「做了什么」。** 本项目的 javadoc 大量记录*设计取舍*和
  *不这么做会出什么问题*。比如「为什么 NPC 不可被推动」那段解释的是原版推挤是 N 对 1 的，
  单纯加大推力赢不了。这类信息是代码本身表达不出来的，请保持这个习惯。
  不要写「这行把 x 加一」这种复述代码的注释。
- **不要用 emoji。**
- **新增任何面向玩家的文字，必须同时加进 `en_us.json` 和 `zh_cn.json`。**
- 私有静态工具方法放在使用它的方法下方，与现有布局一致。

---

## 9. 项目结构速查

```
src/main/java/dev/keyboard/workstations/
├── WorkstationsMod.java        所有方块/物品/实体/创造标签页的注册入口
├── ModConfig.java              config/keyboard_workstations.json 的映射
├── McaSupport.java             MCA 版本门槛检查与不兼容时的进服提示
├── McaVillagers.java           MCA 反射门面（兼容 net.mca 与 fabric.net.mca 两种包名）
├── block/                      方块与方块实体
├── entity/                     工人实体
│   └── ai/                     RancherBrain / FarmerBrain，行为逻辑主体
├── work/                       工作区、设置、普查等领域逻辑
├── network/                    服务端网络处理
└── client/                     渲染与界面（客户端专用）

src/main/resources/
├── fabric.mod.json             模组清单
├── assets/keyboard_workstations/   模型、贴图、语言、方块状态
└── data/
    ├── keyboard_workstations/      配方、战利品表
    └── minecraft/tags/             往原版标签里加东西（比如斧头可挖）
```

**注意**：Java 包名是 `dev.keyboard.workstations`（不带前缀），而 mod id 是
`keyboard_workstations`。两者故意不同。做全局替换时**千万不要**把包名一起改了，
那会毁掉所有 import。

---

## 10. 常见任务的正确起手式

**加一个新方块**：在 `WorkstationsMod` 注册方块 + 物品（+ 方块实体），然后补齐五个资源文件
（blockstate、block model、item model、loot table、recipe），加两种语言的 `block.keyboard_workstations.<名字>`，
需要的话加进 `data/minecraft/tags/blocks/mineable/axe.json`。跑 verify.py 会告诉你漏了哪个。

**加一个设置项**：加到 `ModConfig`（作为默认值来源）和对应的 `StationSettings`/`FarmSettings`
的 `OPTIONS` 列表里，然后加语言键 `config.keyboard_workstations.<名字>` 和 `.tooltip`。
verify.py 会自动推导出这两个键并检查它们存在。

**改工人行为**：逻辑在 `entity/ai/RancherBrain.java` 和 `FarmerBrain.java`。这两个文件很长，
先读文件顶部的 `State`、`Phase`、`Job` 三个枚举及其注释理解整体结构，再动手。
