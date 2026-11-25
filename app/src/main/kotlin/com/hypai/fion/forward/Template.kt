package com.hypai.fion.forward

import com.hypai.fion.store.GroupChoice
import com.hypai.fion.store.TemplateChoice
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

enum class Language(val tag: String) { Zh("zh-Hans"), En("en") }

@Serializable
data class Template(val title: String, val body: String)

enum class TemplateVariable(
    private val zh: String,
    private val en: String,
    private val extract: (event: ForwardEvent, zone: ZoneId, language: Language) -> String,
) {
    From("发件人", "sender", { event, _, language -> event.from.ifBlank { unknownNumber(language) } }),
    Content("正文", "message", { event, _, _ -> event.messageBody }),
    CallCount(
        "来电次数",
        "callCount",
        { event, _, _ ->
            when (event) {
                is ForwardEvent.Sms -> ""
                is ForwardEvent.MissedCall -> event.countToday.toString()
            }
        },
    ),
    Time("时间", "time", { event, zone, _ -> TIME_FORMAT.format(Instant.ofEpochMilli(event.occurredAtMs).atZone(zone)) }),
    Carrier("运营商", "carrier", { event, _, _ -> event.sim.carrier }),
    SimName("SIM卡", "simName", { event, _, _ -> event.sim.name }),
    Number("本机号码", "simNumber", { event, _, _ -> event.sim.number }),
    ;

    fun label(language: Language): String = when (language) {
        Language.Zh -> zh
        Language.En -> en
    }

    fun displayName(language: Language, kind: EventKind? = null): String = when (this) {
        From -> when (language) {
            Language.Zh -> "对方号码"
            Language.En -> if (kind == EventKind.MissedCall) "Caller" else "Sender"
        }
        Content -> when (language) {
            Language.Zh -> "短信内容"
            Language.En -> "Message"
        }
        CallCount -> when (language) {
            Language.Zh -> "未接次数"
            Language.En -> "Missed Count"
        }
        Time -> when (language) {
            Language.Zh -> "日期时间"
            Language.En -> "Date & Time"
        }
        Carrier -> when (language) {
            Language.Zh -> "运营商"
            Language.En -> "Carrier"
        }
        SimName -> when (language) {
            Language.Zh -> "SIM 卡名"
            Language.En -> "SIM Name"
        }
        Number -> when (language) {
            Language.Zh -> "本机号码"
            Language.En -> "SIM Number"
        }
    }

    fun token(language: Language): String = "{${label(language)}}"

    fun appliesTo(kind: EventKind): Boolean = when (this) {
        Content -> kind == EventKind.Sms
        CallCount -> kind == EventKind.MissedCall
        From, Time, Carrier, SimName, Number -> true
    }

    fun resolve(event: ForwardEvent, zone: ZoneId, language: Language): String = extract(event, zone, language)
}

fun EventKind.templateVariables(): List<TemplateVariable> =
    TemplateVariable.entries.filter { it.appliesTo(this) }

data class TemplateToken(
    val start: Int,
    val end: Int,
    val variable: TemplateVariable,
)

fun EventKind.defaultTemplate(language: Language): Template {
    val v = { variable: TemplateVariable -> variable.token(language) }
    return when (this) {
        EventKind.Sms -> when (language) {
            Language.Zh -> Template(
                title = "短信 · ${v(TemplateVariable.From)}",
                body = "${v(TemplateVariable.Content)}\n────────\n" +
                    "到达时间：${v(TemplateVariable.Time)}\n" +
                    "接收号码：${v(TemplateVariable.SimName)} · ${v(TemplateVariable.Number)}",
            )
            Language.En -> Template(
                title = "SMS · ${v(TemplateVariable.From)}",
                body = "${v(TemplateVariable.Content)}\n────────\n" +
                    "Received: ${v(TemplateVariable.Time)}\n" +
                    "To: ${v(TemplateVariable.SimName)} · ${v(TemplateVariable.Number)}",
            )
        }
        EventKind.MissedCall -> when (language) {
            Language.Zh -> Template(
                title = "未接来电 · ${v(TemplateVariable.From)}",
                body = "${v(TemplateVariable.Time)} 呼叫 ${v(TemplateVariable.Number)}，今日第 ${v(TemplateVariable.CallCount)} 次未接",
            )
            Language.En -> Template(
                title = "Missed call · ${v(TemplateVariable.From)}",
                body = "Called ${v(TemplateVariable.Number)} at ${v(TemplateVariable.Time)}, " +
                    "call #${v(TemplateVariable.CallCount)} today",
            )
        }
    }
}

fun EventKind.templateChoice(template: Template): TemplateChoice =
    if (Language.entries.any { defaultTemplate(it) == template }) TemplateChoice.Default else TemplateChoice.Custom(template)

fun TemplateChoice.resolve(kind: EventKind, language: Language): Template = when (this) {
    TemplateChoice.Default -> kind.defaultTemplate(language)
    is TemplateChoice.Custom -> template
}

private val VARIABLES_BY_LABEL = TemplateVariable.entries
    .flatMap { variable -> Language.entries.map { variable.label(it) to variable } }
    .toMap()

private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun findTemplateTokens(template: String): List<TemplateToken> {
    if (!template.contains('{')) return emptyList()
    val tokens = mutableListOf<TemplateToken>()
    var cursor = 0
    val len = template.length
    while (cursor < len) {
        val open = template.indexOf('{', cursor)
        if (open < 0) break
        var scan = open + 1
        while (scan < len && template[scan] != '{' && template[scan] != '}') scan++
        if (scan < len && template[scan] == '}') {
            val variable = VARIABLES_BY_LABEL[template.substring(open + 1, scan).trim()]
            if (variable != null) tokens.add(TemplateToken(open, scan + 1, variable))
            cursor = scan + 1
        } else {
            cursor = if (scan > open + 1) scan else open + 1
        }
    }
    return tokens
}

fun renderTemplate(template: String, values: Map<TemplateVariable, String>): String {
    val tokens = findTemplateTokens(template)
    if (tokens.isEmpty()) return template
    val out = StringBuilder(template.length)
    var cursor = 0
    for (token in tokens) {
        out.append(template, cursor, token.start)
        val replacement = values[token.variable]
        if (replacement != null) out.append(replacement) else out.append(template, token.start, token.end)
        cursor = token.end
    }
    out.append(template, cursor, template.length)
    return out.toString()
}

private fun unknownNumber(language: Language): String = when (language) {
    Language.Zh -> "未知号码"
    Language.En -> "Unknown number"
}

fun ForwardEvent.variables(zone: ZoneId, language: Language): Map<TemplateVariable, String> =
    TemplateVariable.entries.associateWith { it.resolve(this, zone, language) }

fun EventKind.defaultGroup(language: Language): String = when (language) {
    Language.Zh -> when (this) {
        EventKind.Sms -> "短信"
        EventKind.MissedCall -> "未接来电"
    }
    Language.En -> when (this) {
        EventKind.Sms -> "SMS"
        EventKind.MissedCall -> "Missed calls"
    }
}

fun EventKind.groupChoice(name: String, language: Language): GroupChoice =
    if (defaultGroup(language) == name) GroupChoice.Default else GroupChoice.Custom(name)

fun GroupChoice.resolve(kind: EventKind, language: Language): String = when (this) {
    GroupChoice.Default -> kind.defaultGroup(language)
    is GroupChoice.Custom -> name
}
