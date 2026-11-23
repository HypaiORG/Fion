package com.hypai.fion.bark

data class BarkFormErrors(
    val invalidServer: Boolean = false,
    val invalidDeviceKey: Boolean = false,
    val key: KeyLengthMismatch? = null,
) {
    val isValid: Boolean get() = !invalidServer && !invalidDeviceKey && key == null
}

enum class BarkField { Server, DeviceKey }

private data class BarkLink(val server: String, val deviceKey: String)

private val HTTPS_URL_PATTERN = Regex("""https://[^\s'"<>]+""", RegexOption.IGNORE_CASE)
private val JSON_DEVICE_KEY_PATTERN = Regex(""""device_key"\s*:\s*"([^"]+)"""")
private val SHORT_UUID_KEY_PATTERN = Regex("""[2-9A-HJ-NP-Za-km-z]{21,22}""")
private val SERVER_PATTERN = Regex("""[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*(?::(\d{1,5}))?(?:/[A-Za-z0-9._~-]+)*""")
private val DEVICE_KEY_PATTERN = Regex("""[A-Za-z0-9_-]+""")

private fun knownPrefixLength(host: String, path: List<String>, server: String): Int? {
    val known = server.split('/')
    val prefix = known.drop(1)
    return prefix.size.takeIf { known.first().equals(host, ignoreCase = true) && path.take(prefix.size) == prefix }
}

private fun parseBarkLink(text: String, field: BarkField, server: String): BarkLink? {
    val address = HTTPS_URL_PATTERN.find(text)?.value?.drop(8)?.substringBefore('?')?.substringBefore('#') ?: return null
    val host = address.substringBefore('/').ifEmpty { return null }
    val path = address.substringAfter('/', "").split('/').filter(String::isNotEmpty)
    val bodyKey = JSON_DEVICE_KEY_PATTERN.find(text)?.groupValues?.get(1)?.takeIf { path.lastOrNull() == "push" }
    val base = path.lastIndex.takeIf { bodyKey != null }
        ?: path.indexOfFirst(SHORT_UUID_KEY_PATTERN::matches).takeIf { it >= 0 }
        ?: when (field) {
            BarkField.Server -> path.size
            BarkField.DeviceKey -> knownPrefixLength(host, path, server)
                ?: path.lastIndex.takeIf { it >= 0 && address.endsWith('/') }
                ?: 0
        }
    return BarkLink((listOf(host) + path.take(base)).joinToString("/"), bodyKey ?: path.getOrElse(base) { "" })
}

private fun insertedText(before: String, after: String): String {
    val start = before.commonPrefixWith(after).length
    val end = after.length - before.drop(start).commonSuffixWith(after.drop(start)).length
    return after.substring(start, end)
}

fun BarkSettings.valueOf(field: BarkField): String = when (field) {
    BarkField.Server -> server
    BarkField.DeviceKey -> deviceKey
}

fun BarkSettings.withInput(field: BarkField, text: String): BarkSettings {
    val link = parseBarkLink(insertedText(valueOf(field), text), field, normalizedServer())
    return when {
        link != null -> copy(server = link.server, deviceKey = link.deviceKey.ifEmpty { deviceKey })
        field == BarkField.Server -> copy(server = text)
        else -> copy(deviceKey = text)
    }
}

fun BarkSettings.normalizedServer(): String {
    val trimmed = server.trim()
    val withoutScheme = if (trimmed.startsWith("https://", ignoreCase = true)) trimmed.drop(8) else trimmed
    return withoutScheme.trimEnd('/')
}

fun BarkSettings.displayHost(): String =
    (endpoint()?.host ?: normalizedServer().substringBefore('/')).ifBlank { DEFAULT_BARK_SERVER }

private fun BarkSettings.normalized(): BarkSettings {
    val host = normalizedServer()
    val key = deviceKey.trim()
    return copy(server = host, deviceKey = parseBarkLink(key, BarkField.DeviceKey, host)?.deviceKey ?: key.trim('/'))
}

private fun isValidServer(server: String): Boolean {
    val port = SERVER_PATTERN.matchEntire(server)?.groupValues?.get(1) ?: return false
    return port.isEmpty() || port.toInt() in 1..65535
}

private fun BarkSettings.normalizedErrors(): BarkFormErrors = BarkFormErrors(
    invalidServer = !isValidServer(server),
    invalidDeviceKey = deviceKey.isNotEmpty() && !DEVICE_KEY_PATTERN.matches(deviceKey),
    key = aes.keyError(),
)

fun BarkSettings.formErrors(): BarkFormErrors = normalized().normalizedErrors()

fun BarkSettings.endpoint(): BarkEndpoint? {
    val input = normalized()
    if (input.deviceKey.isEmpty() || !input.normalizedErrors().isValid) return null
    return BarkEndpoint(input.server, input.deviceKey, input.aes.takeIf { it.key.isNotEmpty() })
}
