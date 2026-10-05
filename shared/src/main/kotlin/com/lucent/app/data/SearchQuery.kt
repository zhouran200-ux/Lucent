package com.lucent.app.data

data class SearchQuery(
    val terms: List<String> = emptyList(),
    val phrases: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val flags: Set<String> = emptySet(),
    val has: Set<String> = emptySet(),
    val linkTo: String? = null
) {

    val isEmpty: Boolean
        get() = terms.isEmpty() && phrases.isEmpty() && tags.isEmpty() && flags.isEmpty() &&
            has.isEmpty() && linkTo == null

    private val needles: List<String> by lazy { terms + phrases }

    val isNoteOnly: Boolean
        get() = tags.isNotEmpty() || linkTo != null ||
            "archived" in flags || "checklist" in flags

    val sqlText: String
        get() = (phrases + terms).maxByOrNull { it.length } ?: ""

    val sqlTag: String
        get() = tags.firstOrNull() ?: ""

    val sqlArchived: Int
        get() = if ("archived" in flags) 1 else FILTER_ANY

    val sqlTrashed: Int get() = FILTER_ANY

    fun matches(note: Note): Boolean {
        if (isEmpty) return true

        val checklist =
            if (note.isChecklist || "checklist" in has) Checklist.parse(note.checklist) else emptyList()

        val haystack = noteHaystack(note, checklist)
        if (terms.any { !haystack.contains(it) }) return false
        if (phrases.any { !haystack.contains(it) }) return false

        val noteTags = note.tags.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (tags.any { wanted -> noteTags.none { it == wanted || it.contains(wanted) } }) return false

        if ("pinned" in flags && !note.pinned) return false
        if ("checklist" in flags && !note.isChecklist) return false
        if ("archived" in flags && !note.archived) return false

        if ("attachment" in has && Attachments.parse(note.attachments).isEmpty()) return false

        linkTo?.let { target ->
            if (NoteLinks.linkTargets(note).none { it.lowercase().contains(target) }) return false
        }
        return true
    }

    fun rank(note: Note): Int {
        if (isEmpty) return 0
        val title = note.title.lowercase()
        val tagText = note.tags.lowercase()
        val body = noteBodyText(note, if (note.isChecklist) Checklist.parse(note.checklist) else emptyList()).lowercase()
        var score = 0
        needles.forEach { needle ->
            if (title == needle) score += 100
            else if (title.startsWith(needle)) score += 50
            else if (title.contains(needle)) score += 30
            if (tagText.contains(needle)) score += 12
            if (body.contains(needle)) score += 5
        }
        return score
    }

    private fun noteBodyText(note: Note, checklist: List<ChecklistItem>): String =
        if (note.isChecklist) (checklist.joinToString(" ") { it.text } + " " + note.body) else note.body

    private fun noteHaystack(note: Note, checklist: List<ChecklistItem>): String =
        listOf(note.title, noteBodyText(note, checklist), note.tags).joinToString(" ").lowercase()

    companion object {

        const val FILTER_ANY = -1
        const val NO_TIME_FILTER = -1L

        val HINTS: List<String> = listOf(
            "tag:", "is:pinned", "is:archived", "is:checklist",
            "has:attachment", "link:"
        )

        val HELP: List<Pair<String, String>>
            get() = listOf(
                "milk bread" to com.lucent.app.i18n.S.helpBothWords,
                "\"shopping list\"" to com.lucent.app.i18n.S.helpExactPhrase,
                "tag:work  #work" to com.lucent.app.i18n.S.helpTag,
                "is:pinned" to com.lucent.app.i18n.S.helpPinned,
                "is:checklist" to com.lucent.app.i18n.S.helpChecklist,
                "is:archived" to com.lucent.app.i18n.S.helpArchived,
                "has:attachment" to com.lucent.app.i18n.S.helpHasAttachment,
                "link:Recipes" to com.lucent.app.i18n.S.helpLink,
                "清单 / チェックリスト / 체크리스트" to com.lucent.app.i18n.S.helpLocalizedFilters
            )

        private val LOCALIZED_TOKENS: Map<String, String> = buildMap<String, String> {
            fun alias(canonical: String, vararg spellings: String) {
                spellings.forEach { put(it.lowercase(), canonical) }
            }
            alias("is:pinned", "已置顶", "置顶", "ピン留め", "固定", "고정됨", "고정")
            alias("is:archived", "已归档", "归档", "アーカイブ", "보关됨", "보관")
            alias("is:checklist", "清单", "チェックリスト", "체크리스트")
            alias("has:attachment", "有附件", "附件", "添付あり", "添付", "첨부있음", "첨부")
        }

        private val LOCALIZED_FIELDS: Map<String, String> = buildMap<String, String> {
            fun alias(canonical: String, vararg spellings: String) {
                spellings.forEach { put(it.lowercase(), canonical) }
            }
            alias("tag", "标签", "タグ", "태그")
            alias("is", "是", "状态", "状態", "상태")
            alias("has", "有", "含", "あり", "포함")
            alias("link", "链接", "リンク", "링크")
        }

        private val LOCALIZED_VALUES: Map<String, Map<String, String>> = mapOf(
            "is" to buildMap<String, String> {
                listOf(
                    "已置顶" to "pinned", "置顶" to "pinned", "ピン留め" to "pinned", "固定" to "pinned", "고정" to "pinned", "고정됨" to "pinned",
                    "已归档" to "archived", "归档" to "archived", "アーカイブ" to "archived", "보관됨" to "archived", "보관" to "archived",
                    "清单" to "checklist", "チェックリスト" to "checklist", "체크리스트" to "checklist"
                ).forEach { (k, v) -> put(k.lowercase(), v) }
            },
            "has" to buildMap<String, String> {
                listOf(
                    "附件" to "attachment", "添付" to "attachment", "첨부" to "attachment"
                ).forEach { (k, v) -> put(k.lowercase(), v) }
            }
        )

        private fun canonicalizeToken(raw: String): String {
            val token = raw.replace('：', ':').replace('＃', '#')

            LOCALIZED_TOKENS[token.lowercase()]?.let { return it }

            val colon = token.indexOf(':')
            if (colon <= 0 || colon == token.lastIndex) return token

            val rawField = token.substring(0, colon).lowercase()
            val rawValue = token.substring(colon + 1)
            val field = LOCALIZED_FIELDS[rawField] ?: rawField
            if (field == "tag" || field == "link") return "$field:$rawValue"
            val value = LOCALIZED_VALUES[field]?.get(rawValue.lowercase()) ?: rawValue
            return "$field:$value"
        }

        private val TOKEN = Regex("\"([^\"]*)\"|(\\S+)")

        fun parse(raw: String): SearchQuery {
            val text = raw.trim()
            if (text.isEmpty()) return SearchQuery()

            val terms = mutableListOf<String>()
            val phrases = mutableListOf<String>()
            val tags = mutableListOf<String>()
            val flags = mutableSetOf<String>()
            val has = mutableSetOf<String>()
            var linkTo: String? = null

            for (match in TOKEN.findAll(text)) {
                val quoted = match.groupValues[1]
                if (match.value.startsWith("\"")) {
                    if (quoted.isNotBlank()) phrases += quoted.trim().lowercase()
                    continue
                }

                val token = canonicalizeToken(match.groupValues[2])
                if (token.isBlank()) continue

                if (token.length > 1 && token.startsWith('#')) {
                    tags += token.removePrefix("#").lowercase()
                    continue
                }

                val colon = token.indexOf(':')
                if (colon <= 0 || colon == token.lastIndex) {
                    terms += token.lowercase()
                    continue
                }

                val field = token.substring(0, colon).lowercase()
                val value = token.substring(colon + 1).lowercase()
                when (field) {
                    "tag" -> tags += value
                    "is" -> if (value in setOf("pinned", "checklist", "archived")) {
                        flags += value
                    } else {
                        terms += token.lowercase()
                    }
                    "has" -> if (value in setOf("attachment", "attachments", "checklist")) {
                        has += when (value) {
                            "attachments" -> "attachment"
                            else -> value
                        }
                    } else {
                        terms += token.lowercase()
                    }
                    "link", "links" -> linkTo = value
                    else -> terms += token.lowercase()
                }
            }

            return SearchQuery(
                terms = terms,
                phrases = phrases,
                tags = tags,
                flags = flags,
                has = has,
                linkTo = linkTo
            )
        }
    }
}

@JvmName("filterNotesBySearch")
fun List<Note>.filterBySearch(query: SearchQuery): List<Note> = filter { query.matches(it) }
