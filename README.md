<div align="center">

[🇨🇳 简体中文](./README.md) · [English](./docs/README.en.md) · [🇯🇵 日本語](./docs/README.ja.md) · [🇰🇷 한국어](./docs/README.ko.md)

</div>

<div align="center">

# 𝓛𝓾𝓬𝓮𝓷𝓽

### 现代 · 极简 · 恰到好处的深厚内功

**一款让 AI 助手能够真正触达你数据的本地笔记与知识库应用。所有的文字与数据全部封存在你设备本地的加密数据库中；支持四大语言；配备面向 2026 年新一代模型的“三层深度推理架构”与毫秒级流式调度引擎；在轻盈如晨雾的流体毛玻璃质感之下，安放你对隐私、秩序与思考的全部挑剔。**

![Lucent — Platform, Build, Interface, Assistant, Privacy, License](badges/badges.svg)

</div>

---

## 设计初衷

大多数笔记应用总在礼貌地要求用户在三种美德中“三选其二”：美观、隐私、或是智能。选好你的组合，然后对剩下的缺陷逆来顺受。

Lucent 委婉地谢绝了这种妥协。

你写下的每一个字，都保存在绝不离开你设备本地的加密数据库中。你的 AI 助手可以是你自己配置的云端顶尖大模型（OpenAI、Anthropic Claude、Google Gemini、DeepSeek、通义千问等），也可以是设备本地离线运行的轻量模型，或者干脆彻底静默——倘若你完全不需要它，这依然是一款纯粹、敏捷且极具美感的离线笔记应用。

---

## 凝练的工程架构：单一真相源

Lucent 遵循严谨的现代 Android 与 Kotlin 多端工程规范，坚持以 `shared` 为核心代码真相源：

- **`:app`** —— Android 专属壳工程（Kotlin、Jetpack Compose、Room over SQLCipher、通过 NDK 接入的轻量原生加速库）。负责生命周期、桌面微件（Widgets）、权限与系统级通道。
- **`:shared`** —— 承载应用核心灵魂的单一源码目录：所有业务逻辑、数据库实体、核心界面、2026 三层深度推理调度系统、时间窗流式渲染引擎以及全套四语言翻译。
- **`rust/`** —— 极速密码学加速器，通过 JNI 实现关键备份与密文处理，同时保留 100% 等效的纯 Kotlin 安全降级实现，确保在任何构建环境下均能顺畅编译。

---

## 懂思考、有分寸的随身助手

这是定义 Lucent 体验的核心。带上你自己的 API 密钥——原生支持 OpenAI、Anthropic Claude、Google Gemini、DeepSeek 与通义千问协议，多套方案一键无缝切换。

### 1. 2026 三层深度推理架构 (Three-Layer Reasoning)
面对 2026 年后各大模型厂商在“思考（Thinking）协议”上的严重碎片化，Lucent 彻底摈弃了将底层参数直接暴露在 UI 上的粗糙做法，建立了工业级的四段式映射流水线：

```
用户产品意图 (ReasoningPreset)
   │  自动 (AUTO) · 极速 (FAST) · 标准 (BALANCED) · 深度 (DEEP) · 极致 (MAXIMUM)
   ▼
跨模型语义对齐门面 (ReasoningResolver)
   │
   ├──────────────────────────────┐
   ▼                              ▼
模型能力注册表 (Capability Registry)  厂商原生适配器 (Provider Adapter)
   │  支持状态 · 协议类型 · 阶梯映射        │  OpenAI · Claude · Gemini · DeepSeek
   └──────────────┬───────────────┘
                  ▼
          底层原生 Wire Format
   (reasoning.effort / thinkingLevel / output_config.effort / budget_tokens)
```

- **第一层：用户意图层 (`ReasoningPreset`)**：
  提供“✨ 自动”、“⚡ 极速”、“⚖️ 标准”、“🧠 深度”、“🚀 极致”五档产品意图。其中 **AUTO 坚持零参数侵入原则**，绝不武断硬编码为 medium，百分之百交由模型发挥最佳原生自适应策略。
- **第二层：模型能力注册表 (`ModelCapabilityRegistry`)**：
  以数据结构精准定义各模型的思考协议（如区分原生推理模型 `o1`/`gpt-6`/`deepseek-r1`/`gemini-3.5-flash` 与纯对话模型 `gpt-4o`/`gemini-1.5`）。支持在拉取模型列表时**热加载（Hot-reload）**动态能力元数据。
- **第三层：厂商原生参数映射 (`ReasoningMapper`)**：
  - **OpenAI / ChatGPT**：精准注入 `reasoning_effort` 与 `reasoning: { mode: "pro", effort }`；
  - **Anthropic Claude**：自动区隔 Claude 3.7+ 的 `output_config.effort` 与 Claude 3.5 的 `budget_tokens`；
  - **Google Gemini**：在 `generationConfig.thinkingConfig` 中精准注入 `thinkingLevel`；
  - **DeepSeek**：遵循官方服务端规范，实现从标准档向 `high` 的平滑阶梯映射；
  - **未知中转端点**：启发式兜底，不产生非法字段，杜绝 400 报错。
- **正交的思考 Token 预算控制**：
  在对话设置中，支持为高级用户提供独立的思考 Token 预算上限选项（2K 至 64K），与思考强度解耦，实现对生成成本与推演深度的双重掌控。

---

## 毫秒级帧调度流式引擎

在长文本问答与实时思维链推演时，高频的 SSE 增量事件如果直接触发 Compose 重组，会引发剧烈的掉帧与电量消耗。

Lucent 实现了生产级的 **`StreamUiScheduler`** 流式刷新调度器：

1. **协议层颗粒度解耦**：底层 SSE 逐行解析，在网络层将正文增量（`deltaText`）与思考链增量（`deltaReasoning`）清晰拆解；
2. **时间窗批量消费**：采用“增量 StringBuilder 缓冲区 + 35ms 帧时间窗”的批量消费机制，高频 Token 进入缓冲区，UI 仅按人眼最舒适的节奏批量更新 State；
3. **Markdown 渲染防冲击**：避免长文本频繁全量重复解析 Markdown AST，在保障打字机实时灵动感的同时，确保 60/120 帧极致流畅；
4. **思维链折叠展示**：思考过程在正文上方以深色毛玻璃卡片清晰折叠，实时标注用时与原生模型标签，支持一键展开回顾严密的逻辑推演。

---

## 沉淀记忆的本地笔记本与知识库

笔记本不仅是分类夹，更是与 AI 协作的专属上下文空间：

- **无损自动提取 Markdown**：向笔记本添加参考文档时，本地静默提取标题、段落、层级列表与表格，自动转换为排版工整的 Markdown 知识库；
- **全文本 100% 完整挂载（零截断原则）**：顺应 2026 年新一代模型的百万级超长上下文特性，笔记本内的全部关联资料全文注入系统提示词，绝不进行截断与信息压缩，确保问答论据精准详实；
- **版本快照与时光倒流**：每一次关键修改都会在本地保留历史快照，随时可以对比变更并一键恢复，再也不必担心误操作丢失灵感；
- **双向链接与灵活排版**：输入 `[[笔记标题]]` 即可建立双向关联；支持标签、多色封面标记、置顶与归档。

---

## 真正管用的任务与待办

- **自然语言解析**：在输入框随手输入“下周五下午三点开会”，即可自动解析为精确的截止时间戳与系统级提醒闹铃；
- **多层级清单与周期循环**：支持子任务、优先级以及重启后依然存活的系统级提醒，周期循环任务绝不半途失效；
- **任务归档**：完成待办时，其所属检查项联动划掉，历史任务平滑归档至专属视图。

---

## 流体玻璃美学与现代交互

- **流体渐变与动态虚化**：采用基于 Haze 的现代磨砂毛玻璃材质，搭配随韵律缓缓舒展的动态流体背景，优雅耐看；
- **深色沉浸感与多色主题**：预置多组精心调配的色彩家族，支持浅色、深色、跟随系统，并在 Android 12+ 上自然融合壁纸取色（Material You / Monet）；
- **生产级软键盘避让机制**：
  - 彻底规避 Android Compose 中常见的底栏与键盘双重 Insets 导致的“悬空空白断层”缺陷；
  - 键盘弹起时智能平滑吸附最新消息，并在轻触消息列表空白处时即刻顺畅收起，交互体验极其温润。

---

## 从底层守护的隐私与锁

- **静态全盘加密**：数据库底层采用 SQLCipher（AES-256）全盘加密，即使设备被物理读取也无法窥见明文；
- **强固的应用防护**：阶梯式防暴力破解冷却时间、重启后依然维持的计数器、支持指纹生物识别认证；
- **自主可控的数据备份**：单文件密码保护的 `.lcb` 备份格式，完整打包笔记、待办、对话记录与偏好设置，导入时具备预检确认机制，数据迁徙自由无拘。

---

## 本地代码目录结构

```
shared/       核心业务与 UI 源码目录 —— 业务逻辑、Compose UI、Room、数据模型与 2026 推理映射器
app/          Android 原生模块 —— Activity 宿主、平台配置、原生通道与 Android 平台实现
rust/         Rust 原生加速层（JNI 密码学计算与安全数学支持）
.github/      CI/CD 工作流定义文件
```

---

## 构建与测试验证

应用采用现代 Gradle（Kotlin DSL）构建体系：

- **编译全工程**：
  ```bash
  gradle assembleDebug
  ```
- **执行完整单元测试与架构验证套件**（含三层推理映射与流式调度器测试）：
  ```bash
  gradle :app:testDebugUnitTest
  ```

---

## 开源致谢与借景之美

在精致的界面之下，Lucent 深深得益于开源社区的优秀成果：

| 开源项目 | 承担职责 | 许可证 |
|---|---|---|
| [Kotlin](https://github.com/JetBrains/kotlin) & [Coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 开发语言、协程并发与异步流体系 | Apache-2.0 |
| [Jetpack Compose & AndroidX](https://developer.android.com/jetpack/androidx) | 现代化响应式声明式 UI 框架 | Apache-2.0 |
| [Material Icons](https://github.com/google/material-design-icons) | 视觉符号与操作图标 | Apache-2.0 |
| [Haze](https://github.com/chrisbanes/haze) | 现代毛玻璃磨砂渲染支持 | Apache-2.0 |
| [OkHttp](https://github.com/square/okhttp) | 跨厂商 HTTP 与 SSE 长连接网络流通信 | Apache-2.0 |
| [SQLite](https://www.sqlite.org/) & [SQLCipher](https://www.zetetic.net/sqlcipher/) | 本地数据库引擎与底层静态加密锁 | Public Domain / BSD-style |
| [Room](https://developer.android.com/training/data-storage/room) | 本地持久化对象关系映射与响应式查询 | Apache-2.0 |

---

## 许可证 (License)

Lucent 基于 **[MIT 许可证](./LICENSE)** 开源发布。你可以自由使用、修改、分发甚至用于商业项目，唯一的请求是保留原版权声明与许可证文本。
