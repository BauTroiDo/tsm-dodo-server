package tw.tsm.app.handlers

/** Original DVT UI restricts home name to 8 and signature to 12 characters. */
object HouseSettingsPolicy {
    fun valid(name: String, signature: String): Boolean =
        name.isNotBlank() && name.length <= 8 &&
            signature.length <= 12 && listOf(name, signature).all { text ->
                text.none { it.isISOControl() || it == '<' || it == '>' }
            }
}
