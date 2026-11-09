package app.ios.messages.data

/** Finds one-time codes in verification messages (English + Arabic keywords). */
object Otp {
    private val keywords = listOf(
        "code", "otp", "verification", "verify", "passcode", "password", "pin", "one-time", "one time",
        "security", "authenticate", "login", "log in", "sign in",
        "رمز", "كود", "التحقق", "تحقق", "تأكيد", "كلمة المرور", "كلمة السر", "الدخول"
    )
    private val grouped = Regex("""(?<![\d])(\d{3})[- ](\d{3})(?![\d])""")
    private val plain = Regex("""(?<![\d.,+])(\d{4,8})(?![\d])""")

    fun extract(body: String): String? {
        if (body.length > 400) return null
        val lower = body.lowercase()
        if (keywords.none { lower.contains(it) }) return null
        grouped.find(body)?.let { return it.groupValues[1] + it.groupValues[2] }
        return plain.find(body)?.groupValues?.get(1)
    }
}
