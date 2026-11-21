package com.hypai.fion.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable

enum class Language(val tag: String) { Zh("zh-Hans"), En("en") }

@Serializable
data class Template(val title: String, val body: String)

enum class TemplateVariable(private val zh: String, private val en: String) {
    From("来源号码", "sender"),
    Content("短信内容", "message"),
    CallCount("来电次数", "callCount"),
    Time("时间", "time"),
    Carrier("运营商", "carrier"),
    SimName("SIM卡", "simName"),
    Number("电话号码", "simNumber"),
    ;

    fun label(language: Language): String = when (language) {
        Language.Zh -> zh
        Language.En -> en
    }

    fun token(language: Language): String = "{{${label(language)}}}"
}

fun EventKind.defaultTemplate(language: Language): Template {
    val v = { variable: TemplateVariable -> variable.token(language) }
    return when (this) {
        EventKind.Sms -> when (language) {
            Language.Zh -> Template(
                title = "短信 · ${v(TemplateVariable.From)}",
                body = "${v(TemplateVariable.Content)}\n────────\n" +
                    "接收时间：${v(TemplateVariable.Time)}\n" +
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

private val placeholder = Regex("""\{\{([^{}]*)\}\}""")
private val variablesByLabel = TemplateVariable.entries
    .flatMap { variable -> Language.entries.map { variable.label(it) to variable } }
    .toMap()

fun renderTemplate(template: String, values: Map<TemplateVariable, String>): String =
    placeholder.replace(template) { match ->
        variablesByLabel[match.groupValues[1].trim()]?.let(values::get) ?: match.value
    }

private fun unknownNumber(language: Language): String = when (language) {
    Language.Zh -> "未知号码"
    Language.En -> "Unknown number"
}

private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

fun ForwardEvent.variables(zone: ZoneId, language: Language): Map<TemplateVariable, String> =
    TemplateVariable.entries.associateWith { variable ->
        when (variable) {
            TemplateVariable.From -> from.ifBlank { unknownNumber(language) }
            TemplateVariable.Content -> smsContent
            TemplateVariable.CallCount -> when (this) {
                is ForwardEvent.Sms -> ""
                is ForwardEvent.MissedCall -> countToday.toString()
            }
            TemplateVariable.Time -> timeFormat.format(Instant.ofEpochMilli(occurredAtMs).atZone(zone))
            TemplateVariable.Carrier -> sim.carrier
            TemplateVariable.SimName -> sim.name
            TemplateVariable.Number -> sim.number
        }
    }

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
