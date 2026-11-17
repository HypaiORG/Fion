package com.hypai.fion.bark

data class BarkFormErrors(
    val invalidServer: Boolean = false,
    val invalidDeviceKey: Boolean = false,
    val key: KeyLengthMismatch? = null,
) {
    val isValid: Boolean get() = !invalidServer && !invalidDeviceKey && key == null
}

enum class BarkField { Server, DeviceKey }

private val SERVER_PATTERN = Regex("""[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)*(?::(\d{1,5}))?(?:/[A-Za-z0-9._~-]+)*""")
private val DEVICE_KEY_PATTERN = Regex("""[A-Za-z0-9_-]+""")

fun BarkSettings.valueOf(field: BarkField): String = when (field) {
    BarkField.Server -> server
    BarkField.DeviceKey -> deviceKey
}

fun BarkSettings.withInput(field: BarkField, text: String): BarkSettings = when (field) {
    BarkField.Server -> copy(server = text)
    BarkField.DeviceKey -> copy(deviceKey = text)
}

fun BarkSettings.normalizedServer(): String {
    val trimmed = server.trim()
    val withoutScheme = if (trimmed.startsWith("https://", ignoreCase = true)) trimmed.drop(8) else trimmed
    return withoutScheme.trimEnd('/')
}

fun BarkSettings.displayHost(): String =
    (endpoint()?.host ?: normalizedServer().substringBefore('/')).ifBlank { DEFAULT_BARK_SERVER }

private fun BarkSettings.normalized(): BarkSettings = copy(
    server = normalizedServer(),
    deviceKey = deviceKey.trim().trim('/'),
)

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
