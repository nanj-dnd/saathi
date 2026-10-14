package app.saathi.safety

/**
 * Text checks on the serialized screen, used to decide what may be answered on the phone.
 * Scripted steps and cached answers skip the model, and the model is what notices scams and
 * payment screens, so anything that looks like either goes to the model instead.
 */
object ScreenSafety {
    private val IC = setOf(RegexOption.IGNORE_CASE)

    // Buttons that only the senior presses: pointing at them is never allowed, whatever a recipe says.
    private val sensitiveTarget = Regex(
        "\\b(pay|pin|otp|cvv|password|passcode)\\b|pay now|proceed to pay|place order|confirm payment|" +
            "भुगतान|पेमेंट|ओटीपी|पिन|पासवर्ड|பணம் செலுத்து|చెల్లించు|ಪಾವತಿಸಿ|പണമടയ്ക്കുക",
        IC,
    )

    // Codes and secrets on screen: the answer may depend on them, so don't reuse an old one.
    private val sensitiveScreen = Regex(
        "\\botp\\b|one[- ]time|upi pin|\\bpin\\b|\\bcvv\\b|password|passcode|card number|" +
            "ओटीपी|पिन|पासवर्ड|ஓடிபி|ఓటీపీ|ಒಟಿಪಿ|ഒടിപി",
        IC,
    )

    // What the fraud scripts against seniors put on screen. The model knows how to warn about these.
    private val scamCue = Regex(
        "anydesk|teamviewer|quicksupport|rustdesk|airdroid|screen ?shar|digital arrest|\\bkyc\\b|" +
            "lottery|jackpot|you have won|prize money|lucky draw|\\bcbi\\b|narcotics|customs duty|" +
            "electricity (will be )?(cut|disconnect)|power (will be )?(cut|disconnect)|collect request|request money|" +
            "केवाईसी|लॉटरी|इनाम|डिजिटल अरेस्ट|बिजली (कट|काट)|कनेक्शन (कट|काट)|" +
            "லாட்டரி|பரிசு விழுந்|లాటరీ|బహుమతి గెలు|ಲಾಟರಿ|ಬಹುಮಾನ ಗೆದ್ದ|ലോട്ടറി|സമ്മാനം ലഭിച്ചു",
        IC,
    )

    fun isSensitiveTarget(label: String): Boolean = sensitiveTarget.containsMatchIn(label)
    fun sensitive(tree: String): Boolean = sensitiveScreen.containsMatchIn(tree)
    fun scamCue(tree: String): Boolean = scamCue.containsMatchIn(tree)
}
