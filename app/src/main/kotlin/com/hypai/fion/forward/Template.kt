package com.hypai.fion.forward

import com.hypai.fion.store.GroupChoice
import com.hypai.fion.store.TemplateChoice
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class Language(val tag: String) { Zh("zh-Hans"), En("en") }

private val SMS_ONLY = setOf(EventKind.Sms)
private val CALL_ONLY = setOf(EventKind.MissedCall)
private val ALL_KINDS = EventKind.entries.toSet()

object TemplateVariableSerializer : KSerializer<TemplateVariable> {
    override val descriptor = PrimitiveSerialDescriptor("TemplateVariable", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: TemplateVariable) = encoder.encodeString(value.id)

    override fun deserialize(decoder: Decoder): TemplateVariable {
        val id = decoder.decodeString()
        return TemplateVariable.fromId(id) ?: throw SerializationException("Unknown template variable: $id")
    }
}

@Serializable(with = TemplateVariableSerializer::class)
enum class TemplateVariable(
    val id: String,
    val requirement: AppPermission,
    val kinds: Set<EventKind>,
    private val zh: String,
    private val en: String,
    private val extract: (event: ForwardEvent, zone: ZoneId, language: Language) -> String,
) {
    SmsFrom("sms.from", AppPermission.ReceiveSms, SMS_ONLY, "对方号码", "Sender", { event, _, language ->
        if (event is ForwardEvent.Sms) event.from.ifBlank { unknownNumber(language) } else ""
    }),
    SmsContent("sms.content", AppPermission.ReceiveSms, SMS_ONLY, "短信内容", "Message", { event, _, _ ->
        (event as? ForwardEvent.Sms)?.content.orEmpty()
    }),
    SmsTime("sms.time", AppPermission.ReceiveSms, SMS_ONLY, "日期时间", "Date & Time", { event, zone, _ ->
        if (event is ForwardEvent.Sms) formatTime(event.occurredAtMs, zone) else ""
    }),
    CallFrom("call.from", AppPermission.ReadCallLog, CALL_ONLY, "对方号码", "Caller", { event, _, language ->
        if (event is ForwardEvent.MissedCall) event.from.ifBlank { unknownNumber(language) } else ""
    }),
    CallCount("call.count", AppPermission.ReadCallLog, CALL_ONLY, "未接次数", "Missed Count", { event, _, _ ->
        (event as? ForwardEvent.MissedCall)?.countToday?.toString().orEmpty()
    }),
    CallTime("call.time", AppPermission.ReadCallLog, CALL_ONLY, "日期时间", "Date & Time", { event, zone, _ ->
        if (event is ForwardEvent.MissedCall) formatTime(event.occurredAtMs, zone) else ""
    }),
    SimCarrier("sim.carrier", AppPermission.ReadPhoneState, ALL_KINDS, "运营商", "Carrier", { event, _, _ ->
        event.sim.carrier
    }),
    SimName("sim.name", AppPermission.ReadPhoneState, ALL_KINDS, "SIM 卡名", "SIM Name", { event, _, _ ->
        event.sim.name
    }),
    SimNumber("sim.number", AppPermission.ReadPhoneNumbers, ALL_KINDS, "本机号码", "SIM Number", { event, _, _ ->
        event.sim.number
    }),
    ;

    val permission: PermissionGroup get() = requirement.group

    fun displayName(language: Language): String = when (language) {
        Language.Zh -> zh
        Language.En -> en
    }

    fun appliesTo(kind: EventKind): Boolean = kind in kinds

    fun resolve(event: ForwardEvent, zone: ZoneId, language: Language): String = extract(event, zone, language)

    companion object {
        private val byId = entries.associateBy(TemplateVariable::id)

        fun fromId(id: String): TemplateVariable? = byId[id]
    }
}

@Serializable
sealed interface TemplatePart {
    @Serializable
    @SerialName("text")
    data class Text(val value: String) : TemplatePart

    @Serializable
    @SerialName("var")
    data class Var(val id: TemplateVariable) : TemplatePart
}

object TemplatePartsSerializer : KSerializer<List<TemplatePart>> {
    private val delegate = ListSerializer(TemplatePart.serializer())
    override val descriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<TemplatePart>) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<TemplatePart> {
        val jsonDecoder = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        val array = jsonDecoder.decodeJsonElement() as? JsonArray
            ?: throw SerializationException("Expected JSON array for template parts")
        val parts = ArrayList<TemplatePart>(array.size)
        for (element in array) {
            val obj = element as? JsonObject ?: continue
            when ((obj["type"] as? JsonPrimitive)?.contentOrNull) {
                "text" -> (obj["value"] as? JsonPrimitive)?.contentOrNull?.let { parts += TemplatePart.Text(it) }
                "var" -> (obj["id"] as? JsonPrimitive)?.contentOrNull
                    ?.let(TemplateVariable::fromId)
                    ?.let { parts += TemplatePart.Var(it) }
            }
        }
        return normalizeParts(parts)
    }
}

@Serializable
data class Template(
    @Serializable(with = TemplatePartsSerializer::class)
    val title: List<TemplatePart>,
    @Serializable(with = TemplatePartsSerializer::class)
    val body: List<TemplatePart>,
) {
    fun usedVariables(kind: EventKind): Set<TemplateVariable> = buildSet {
        for (part in title) if (part is TemplatePart.Var && part.id.appliesTo(kind)) add(part.id)
        for (part in body) if (part is TemplatePart.Var && part.id.appliesTo(kind)) add(part.id)
    }
}

fun normalizeParts(parts: List<TemplatePart>): List<TemplatePart> {
    if (parts.zipWithNext().none { (a, b) -> a is TemplatePart.Text && b is TemplatePart.Text } &&
        parts.none { it is TemplatePart.Text && it.value.isEmpty() }
    ) {
        return parts
    }
    val out = ArrayList<TemplatePart>(parts.size)
    val text = StringBuilder()
    for (part in parts) {
        when (part) {
            is TemplatePart.Text -> text.append(part.value)
            is TemplatePart.Var -> {
                if (text.isNotEmpty()) {
                    out.add(TemplatePart.Text(text.toString()))
                    text.setLength(0)
                }
                out.add(part)
            }
        }
    }
    if (text.isNotEmpty()) out.add(TemplatePart.Text(text.toString()))
    return out
}

fun EventKind.templateVariables(): List<TemplateVariable> =
    TemplateVariable.entries.filter { it.appliesTo(this) }

fun EventKind.defaultTemplate(language: Language, sim: SimIdentity? = null): Template = when (this) {
    EventKind.Sms -> {
        val showName = sim == null || sim.name.isNotBlank()
        val showNumber = sim == null || (sim.number.isNotBlank() && sim.number != sim.name)
        val recipient = buildList {
            if (showName) add(TemplatePart.Var(TemplateVariable.SimName))
            if (showName && showNumber) add(TemplatePart.Text(" · "))
            if (showNumber) add(TemplatePart.Var(TemplateVariable.SimNumber))
        }
        when (language) {
            Language.Zh -> Template(
                title = listOf(TemplatePart.Text("短信 · "), TemplatePart.Var(TemplateVariable.SmsFrom)),
                body = buildList {
                    add(TemplatePart.Var(TemplateVariable.SmsContent))
                    add(TemplatePart.Text("\n────────\n到达时间："))
                    add(TemplatePart.Var(TemplateVariable.SmsTime))
                    if (recipient.isNotEmpty()) {
                        add(TemplatePart.Text("\n接收号码："))
                        addAll(recipient)
                    }
                },
            )
            Language.En -> Template(
                title = listOf(TemplatePart.Text("SMS · "), TemplatePart.Var(TemplateVariable.SmsFrom)),
                body = buildList {
                    add(TemplatePart.Var(TemplateVariable.SmsContent))
                    add(TemplatePart.Text("\n────────\nReceived: "))
                    add(TemplatePart.Var(TemplateVariable.SmsTime))
                    if (recipient.isNotEmpty()) {
                        add(TemplatePart.Text("\nTo: "))
                        addAll(recipient)
                    }
                },
            )
        }
    }
    EventKind.MissedCall -> {
        val target = when {
            sim == null -> TemplateVariable.SimNumber
            sim.number.isNotBlank() -> TemplateVariable.SimNumber
            sim.name.isNotBlank() -> TemplateVariable.SimName
            sim.carrier.isNotBlank() -> TemplateVariable.SimCarrier
            else -> null
        }
        when (language) {
            Language.Zh -> Template(
                title = listOf(TemplatePart.Text("未接来电 · "), TemplatePart.Var(TemplateVariable.CallFrom)),
                body = if (target != null) {
                    listOf(
                        TemplatePart.Var(TemplateVariable.CallTime),
                        TemplatePart.Text(" 呼叫 "),
                        TemplatePart.Var(target),
                        TemplatePart.Text("，今日第 "),
                        TemplatePart.Var(TemplateVariable.CallCount),
                        TemplatePart.Text(" 次未接"),
                    )
                } else {
                    listOf(
                        TemplatePart.Var(TemplateVariable.CallTime),
                        TemplatePart.Text(" 未接来电，今日第 "),
                        TemplatePart.Var(TemplateVariable.CallCount),
                        TemplatePart.Text(" 次未接"),
                    )
                },
            )
            Language.En -> Template(
                title = listOf(TemplatePart.Text("Missed call · "), TemplatePart.Var(TemplateVariable.CallFrom)),
                body = if (target != null) {
                    listOf(
                        TemplatePart.Text("Called "),
                        TemplatePart.Var(target),
                        TemplatePart.Text(" at "),
                        TemplatePart.Var(TemplateVariable.CallTime),
                        TemplatePart.Text(", call #"),
                        TemplatePart.Var(TemplateVariable.CallCount),
                        TemplatePart.Text(" today"),
                    )
                } else {
                    listOf(
                        TemplatePart.Text("Missed at "),
                        TemplatePart.Var(TemplateVariable.CallTime),
                        TemplatePart.Text(", call #"),
                        TemplatePart.Var(TemplateVariable.CallCount),
                        TemplatePart.Text(" today"),
                    )
                },
            )
        }
    }
}

fun EventKind.templateChoice(template: Template): TemplateChoice {
    val normalized = Template(normalizeParts(template.title), normalizeParts(template.body))
    return if (Language.entries.any { defaultTemplate(it) == normalized }) TemplateChoice.Default else TemplateChoice.Custom(normalized)
}

fun TemplateChoice.resolve(kind: EventKind, language: Language, sim: SimIdentity? = null): Template = when (this) {
    TemplateChoice.Default -> kind.defaultTemplate(language, sim)
    is TemplateChoice.Custom -> template
}

fun TemplateChoice.usedVariables(kind: EventKind): Set<TemplateVariable> =
    resolve(kind, Language.En).usedVariables(kind)

private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatTime(epochMs: Long, zone: ZoneId): String =
    TIME_FORMAT.format(Instant.ofEpochMilli(epochMs).atZone(zone))

private fun unknownNumber(language: Language): String = when (language) {
    Language.Zh -> "未知号码"
    Language.En -> "Unknown number"
}

fun renderParts(
    parts: List<TemplatePart>,
    event: ForwardEvent,
    zone: ZoneId,
    language: Language,
): String = buildString {
    for (part in parts) {
        when (part) {
            is TemplatePart.Text -> append(part.value)
            is TemplatePart.Var -> append(part.id.resolve(event, zone, language))
        }
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
