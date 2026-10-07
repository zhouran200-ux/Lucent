package com.lucent.app.data

/**
 * 2026 三层推理架构核心体系 (Model Gateway & Provider Adapter Architecture)
 *
 * 第一层：用户产品意图 (ReasoningPreset) - UI 界面与偏好绑定
 * 第二层：模型能力注册表 (ModelCapabilityRegistry & ModelDefinition)
 * 第三层：厂商原生参数映射器 (ReasoningMapper 体系 -> ResolvedReasoningConfig)
 */

enum class ReasoningPreset(
    val key: String,
    val label: String,
    val desc: String
) {
    AUTO("auto", "自动", "使用模型原厂推荐的最佳自适应策略"),
    FAST("fast", "极速", "优先响应速度，快速输出核心结论"),
    BALANCED("balanced", "标准", "兼顾逻辑严密性与生成效率"),
    DEEP("deep", "深度", "充分展开推演，细致论证每一个步骤"),
    MAXIMUM("maximum", "极致", "调用模型算力极限进行高复杂度逻辑攻坚");

    companion object {
        val DEFAULT = AUTO

        fun fromKey(rawKey: String?): ReasoningPreset {
            if (rawKey.isNullOrBlank()) return DEFAULT
            val clean = rawKey.trim().lowercase()
            return when (clean) {
                "auto", "default" -> AUTO
                "fast", "minimal", "low" -> FAST
                "balanced", "medium" -> BALANCED
                "deep", "high" -> DEEP
                "maximum", "max", "xhigh" -> MAXIMUM
                "none" -> AUTO
                else -> DEFAULT
            }
        }
    }
}

/**
 * 厂商底层思考控制协议类型
 */
enum class ReasoningControl {
    OPENAI_EFFORT,
    ANTHROPIC_EFFORT,
    ANTHROPIC_BUDGET,
    GEMINI_THINKING_LEVEL,
    DEEPSEEK_EFFORT,
    QWEN_EFFORT,
    NONE
}

/**
 * 单个模型的推理能力定义 (Capability Registry Node)
 */
data class ReasoningCapability(
    val supported: Boolean,
    val control: ReasoningControl,
    val supportedPresets: List<ReasoningPreset> = if (supported) ReasoningPreset.entries else listOf(ReasoningPreset.AUTO),
    val defaultPreset: ReasoningPreset = ReasoningPreset.AUTO,
    val isAdaptive: Boolean = true
) {
    companion object {
        val NONE = ReasoningCapability(
            supported = false,
            control = ReasoningControl.NONE,
            supportedPresets = listOf(ReasoningPreset.AUTO)
        )
    }
}

/**
 * 模型核心元数据描述
 */
data class ModelDefinition(
    val id: String,
    val provider: String,
    val reasoning: ReasoningCapability
)

/**
 * 最终解析出的底层原生参数配置
 */
data class ResolvedReasoningConfig(
    val preset: ReasoningPreset,
    val isAuto: Boolean = false,
    val openAiEffort: String? = null,
    val openAiMode: String? = null,
    val geminiThinkingLevel: String? = null,
    val geminiThinkingBudget: Int? = null,
    val claudeEffort: String? = null,
    val claudeBudgetTokens: Int? = null,
    val displayTag: String = ""
) {
    val hasNativeParam: Boolean
        get() = !isAuto && (openAiEffort != null || openAiMode != null ||
                geminiThinkingLevel != null || geminiThinkingBudget != null ||
                claudeEffort != null || claudeBudgetTokens != null)

    companion object {
        val AUTO = ResolvedReasoningConfig(
            preset = ReasoningPreset.AUTO,
            isAuto = true,
            displayTag = "原厂自适应"
        )
    }
}

/**
 * 厂商原生参数适配器标准接口 (Provider Adapter Interface)
 */
interface ReasoningMapper {
    fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig
}

/**
 * OpenAI / ChatGPT 适配器 (支持 o1/o3/o4 与 GPT-6 reasoning.effort & mode: pro)
 */
class OpenAiReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) return ResolvedReasoningConfig.AUTO

        val clean = model.id.lowercase()
        val isGpt6 = clean.contains("gpt-6") || clean.contains("gpt6")
        val effortStr = when (preset) {
            ReasoningPreset.AUTO -> null
            ReasoningPreset.FAST -> "low"
            ReasoningPreset.BALANCED -> "medium"
            ReasoningPreset.DEEP -> "high"
            ReasoningPreset.MAXIMUM -> if (isGpt6) "xhigh" else "high"
        }
        val modeStr = if (isGpt6 && (preset == ReasoningPreset.DEEP || preset == ReasoningPreset.MAXIMUM)) "pro" else null

        return ResolvedReasoningConfig(
            preset = preset,
            openAiEffort = effortStr,
            openAiMode = modeStr,
            displayTag = if (modeStr != null) "OpenAI $effortStr (Pro模式)" else "OpenAI $effortStr"
        )
    }
}

/**
 * Anthropic Claude 适配器 (支持 Claude 3.7+ 自适应 effort 与传统 budget_tokens)
 */
class AnthropicReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) return ResolvedReasoningConfig.AUTO

        return if (model.reasoning.control == ReasoningControl.ANTHROPIC_EFFORT || model.reasoning.isAdaptive) {
            val effort = when (preset) {
                ReasoningPreset.AUTO -> null
                ReasoningPreset.FAST -> "low"
                ReasoningPreset.BALANCED -> "medium"
                ReasoningPreset.DEEP -> "high"
                ReasoningPreset.MAXIMUM -> "max"
            }
            ResolvedReasoningConfig(
                preset = preset,
                claudeEffort = effort,
                displayTag = "Claude $effort (自适应)"
            )
        } else {
            val budget = when (preset) {
                ReasoningPreset.AUTO -> null
                ReasoningPreset.FAST -> 2048
                ReasoningPreset.BALANCED -> 8192
                ReasoningPreset.DEEP -> 16384
                ReasoningPreset.MAXIMUM -> 32768
            }
            ResolvedReasoningConfig(
                preset = preset,
                claudeBudgetTokens = budget,
                displayTag = "Claude ${budget}t 预算"
            )
        }
    }
}

/**
 * Google Gemini 适配器 (支持 thinkingLevel: LOW / MEDIUM / HIGH)
 */
class GeminiReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) return ResolvedReasoningConfig.AUTO

        val level = when (preset) {
            ReasoningPreset.AUTO -> null
            ReasoningPreset.FAST -> "LOW"
            ReasoningPreset.BALANCED -> "MEDIUM"
            ReasoningPreset.DEEP, ReasoningPreset.MAXIMUM -> "HIGH"
        }
        return ResolvedReasoningConfig(
            preset = preset,
            geminiThinkingLevel = level,
            displayTag = "Gemini $level"
        )
    }
}

/**
 * DeepSeek 适配器 (支持 low / high / max 语义对齐)
 */
class DeepSeekReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) return ResolvedReasoningConfig.AUTO

        // DeepSeek 官方支持 low, high, max；标准 (BALANCED) 平滑映射到 high
        val effort = when (preset) {
            ReasoningPreset.AUTO -> null
            ReasoningPreset.FAST -> "low"
            ReasoningPreset.BALANCED -> "high"
            ReasoningPreset.DEEP -> "high"
            ReasoningPreset.MAXIMUM -> "max"
        }
        return ResolvedReasoningConfig(
            preset = preset,
            openAiEffort = effort,
            displayTag = "DeepSeek $effort"
        )
    }
}

/**
 * 通义千问 Qwen 适配器
 */
class QwenReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        if (preset == ReasoningPreset.AUTO) return ResolvedReasoningConfig.AUTO

        val effort = when (preset) {
            ReasoningPreset.AUTO -> null
            ReasoningPreset.FAST -> "low"
            ReasoningPreset.BALANCED -> "medium"
            ReasoningPreset.DEEP, ReasoningPreset.MAXIMUM -> "xhigh"
        }
        return ResolvedReasoningConfig(
            preset = preset,
            openAiEffort = effort,
            displayTag = "Qwen $effort"
        )
    }
}

/**
 * 通用/未知端点安全降级适配器 (零参数侵入)
 */
class FallbackReasoningMapper : ReasoningMapper {
    override fun map(preset: ReasoningPreset, model: ModelDefinition): ResolvedReasoningConfig {
        return ResolvedReasoningConfig(
            preset = preset,
            isAuto = true,
            displayTag = "模型默认"
        )
    }
}

/**
 * 模型能力注册表 (Model Capability Registry)
 * 集中管理各主流模型的思考能力元数据，支持动态注册与启发式嗅探补充
 */
object ModelCapabilityRegistry {
    private val registry = mutableMapOf<String, ModelDefinition>()

    init {
        // 预设主流生产模型能力注册
        registerOpenAiModels()
        registerAnthropicModels()
        registerGeminiModels()
        registerDeepSeekModels()
        registerQwenModels()
    }

    fun register(definition: ModelDefinition) {
        registry[definition.id.lowercase()] = definition
    }

    fun registerBatch(definitions: List<ModelDefinition>) {
        definitions.forEach { register(it) }
    }

    fun getAll(): List<ModelDefinition> {
        return registry.values.toList()
    }

    fun find(modelId: String): ModelDefinition? {
        val clean = modelId.trim().lowercase().removePrefix("models/").substringAfterLast('/')
        return registry[clean]
    }

    fun findOrRegister(modelId: String, provider: String): ModelDefinition {
        val clean = modelId.trim().lowercase().removePrefix("models/").substringAfterLast('/')
        val prov = provider.trim().lowercase()
        val existing = registry[clean]
        if (existing != null) return existing

        // 启发式注册动态嗅探
        val inferredControl = when {
            prov.contains("openai") || prov.contains("chatgpt") || clean.startsWith("o1") || clean.startsWith("o3") || clean.startsWith("o4") || clean.contains("gpt-6") || clean.contains("gpt6") -> {
                ReasoningControl.OPENAI_EFFORT
            }
            prov.contains("claude") || prov.contains("anthropic") || clean.contains("claude") -> {
                if (clean.contains("3-7") || clean.contains("3.7") || clean.contains("4") || clean.contains("5")) {
                    ReasoningControl.ANTHROPIC_EFFORT
                } else {
                    ReasoningControl.ANTHROPIC_BUDGET
                }
            }
            prov.contains("google") || prov.contains("gemini") || clean.contains("gemini") -> {
                if (clean.contains("1.5") || clean.contains("1-5")) ReasoningControl.NONE else ReasoningControl.GEMINI_THINKING_LEVEL
            }
            prov.contains("deepseek") || clean.contains("deepseek") || clean.contains("r1") -> {
                ReasoningControl.DEEPSEEK_EFFORT
            }
            prov.contains("qwen") || clean.contains("qwen") -> {
                ReasoningControl.QWEN_EFFORT
            }
            clean.contains("r1") || clean.contains("think") || clean.contains("reason") -> {
                ReasoningControl.OPENAI_EFFORT
            }
            else -> ReasoningControl.NONE
        }

        val capability = ReasoningCapability(
            supported = inferredControl != ReasoningControl.NONE,
            control = inferredControl,
            supportedPresets = if (inferredControl != ReasoningControl.NONE) ReasoningPreset.entries else listOf(ReasoningPreset.AUTO),
            isAdaptive = true
        )
        val created = ModelDefinition(id = clean, provider = provider, reasoning = capability)
        registry[clean] = created
        return created
    }

    private fun registerOpenAiModels() {
        listOf("o1", "o1-preview", "o1-mini", "o3", "o3-mini", "o4", "gpt-6.1-sol", "gpt-6-astra", "gpt-6-luna").forEach { id ->
            register(ModelDefinition(id, "openai", ReasoningCapability(true, ReasoningControl.OPENAI_EFFORT)))
        }
        listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo").forEach { id ->
            register(ModelDefinition(id, "openai", ReasoningCapability.NONE))
        }
    }

    private fun registerAnthropicModels() {
        listOf("claude-3-7-sonnet", "claude-4-sonnet", "claude-5-sonnet", "claude-opus-5.5").forEach { id ->
            register(ModelDefinition(id, "anthropic", ReasoningCapability(true, ReasoningControl.ANTHROPIC_EFFORT)))
        }
        listOf("claude-3-5-sonnet", "claude-3-opus").forEach { id ->
            register(ModelDefinition(id, "anthropic", ReasoningCapability(true, ReasoningControl.ANTHROPIC_BUDGET, isAdaptive = false)))
        }
        listOf("claude-3-5-haiku", "claude-3-haiku").forEach { id ->
            register(ModelDefinition(id, "anthropic", ReasoningCapability.NONE))
        }
    }

    private fun registerGeminiModels() {
        listOf("gemini-2.0-flash-thinking", "gemini-2.5-pro", "gemini-2.5-flash", "gemini-3.5-flash", "gemini-3.8-flash").forEach { id ->
            register(ModelDefinition(id, "google", ReasoningCapability(true, ReasoningControl.GEMINI_THINKING_LEVEL)))
        }
        listOf("gemini-1.5-pro", "gemini-1.5-flash", "gemini-1.5-flash-8b").forEach { id ->
            register(ModelDefinition(id, "google", ReasoningCapability.NONE))
        }
    }

    private fun registerDeepSeekModels() {
        listOf("deepseek-r1", "deepseek-v4", "deepseek-reasoner").forEach { id ->
            register(ModelDefinition(id, "deepseek", ReasoningCapability(true, ReasoningControl.DEEPSEEK_EFFORT)))
        }
        listOf("deepseek-v3", "deepseek-chat").forEach { id ->
            register(ModelDefinition(id, "deepseek", ReasoningCapability.NONE))
        }
    }

    private fun registerQwenModels() {
        listOf("qwen-max", "qwen-plus", "qwen3.8-omni-flash", "qvq-72b-preview").forEach { id ->
            register(ModelDefinition(id, "qwen", ReasoningCapability(true, ReasoningControl.QWEN_EFFORT)))
        }
    }
}

/**
 * 跨模型语义对齐门面 (ReasoningResolver Facade)
 * 连接 UI 产品意图、能力注册表与对应的厂商 Adapter
 */
object ReasoningResolver {
    private val openAiMapper = OpenAiReasoningMapper()
    private val anthropicMapper = AnthropicReasoningMapper()
    private val geminiMapper = GeminiReasoningMapper()
    private val deepSeekMapper = DeepSeekReasoningMapper()
    private val qwenMapper = QwenReasoningMapper()
    private val fallbackMapper = FallbackReasoningMapper()

    fun resolve(
        preset: ReasoningPreset,
        provider: String,
        model: String,
        customBudgetTokens: Int = 0
    ): ResolvedReasoningConfig {
        // 关键原则 ①：AUTO 且未指定自定义预算时，必须代表“零原生参数侵入，交由模型自适应最佳策略”，绝对不硬编码为 medium
        if (preset == ReasoningPreset.AUTO && customBudgetTokens <= 0) {
            return ResolvedReasoningConfig.AUTO
        }

        val modelDef = ModelCapabilityRegistry.findOrRegister(model, provider)
        val mapper: ReasoningMapper = when (modelDef.reasoning.control) {
            ReasoningControl.OPENAI_EFFORT -> openAiMapper
            ReasoningControl.ANTHROPIC_EFFORT, ReasoningControl.ANTHROPIC_BUDGET -> anthropicMapper
            ReasoningControl.GEMINI_THINKING_LEVEL -> geminiMapper
            ReasoningControl.DEEPSEEK_EFFORT -> deepSeekMapper
            ReasoningControl.QWEN_EFFORT -> qwenMapper
            ReasoningControl.NONE -> fallbackMapper
        }

        val baseConfig = mapper.map(preset, modelDef)
        if (customBudgetTokens > 0) {
            val budgetTag = "预算 ${customBudgetTokens}t"
            val display = if (baseConfig.displayTag.isNotBlank() && baseConfig.displayTag != "原厂自适应") {
                "${baseConfig.displayTag} · $budgetTag"
            } else {
                "自适应 · $budgetTag"
            }
            return baseConfig.copy(
                isAuto = false,
                claudeBudgetTokens = customBudgetTokens,
                geminiThinkingBudget = customBudgetTokens,
                displayTag = display
            )
        }

        return baseConfig
    }
}
