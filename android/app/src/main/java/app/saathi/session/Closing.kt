package app.saathi.session

import app.saathi.capture.ScreenText

/**
 * "No, that's all": the answer to "anything else?" that ends the conversation, in any of
 * Saathi's languages or mixed ("nahi bas, thank you"). Only a reply made entirely of these words
 * counts, so "WhatsApp नहीं खुल रहा" (WhatsApp won't open) is still a request.
 */
object Closing {
    /** Words that say "nothing more". */
    private val CLOSING = setOf(
        // English, and Hinglish as recognisers spell it.
        "no", "nope", "nah", "nothing", "done", "bye", "goodbye", "thanks", "thank", "thankyou", "ok", "okay", "fine",
        "nahi", "nahin", "bas", "shukriya", "dhanyavad", "theek", "thik",
        // Hindi.
        "नहीं", "नही", "ना", "बस", "धन्यवाद", "शुक्रिया", "थैंक", "थैंक्यू", "ठीक", "ओके", "बाय",
        // Tamil, Telugu, Kannada, Malayalam: no / enough / thanks / okay.
        "இல்லை", "வேண்டாம்", "போதும்", "நன்றி", "சரி",
        "లేదు", "వద్దు", "చాలు", "ధన్యవాదాలు", "థ్యాంక్స్", "సరే",
        "ಇಲ್ಲ", "ಬೇಡ", "ಸಾಕು", "ಧನ್ಯವಾದ", "ಧನ್ಯವಾದಗಳು", "ಸರಿ",
        "ഇല്ല", "വേണ്ട", "മതി", "നന്ദി", "ശരി",
    )

    /** Words that may sit around them: "that's all", "कुछ नहीं जी", "हो गया". */
    private val FILLER = setOf(
        "that's", "thats", "that", "it", "is", "all", "for", "now", "you", "very", "much", "so", "i'm", "im", "am", "good", "else", "ji", "kuch", "sab", "ho", "gaya", "hai",
        "जी", "कुछ", "सब", "हो", "गया", "है", "और", "अभी", "यू", "बहुत",
        "ஒன்றும்", "ஒன்னும்", "ரொம்ப", "ஏதும்",
        "ఏమీ", "ఇంకేమీ", "చాలా",
        "ಏನೂ", "ಇನ್ನೇನೂ", "ತುಂಬಾ",
        "ഒന്നും", "വളരെ",
    )

    private const val PUNCTUATION = ".,!?;:।॥\"“”‘’"

    fun isClosing(said: String): Boolean {
        val words = ScreenText.norm(said).split(' ')
            .map { w -> w.trim { it in PUNCTUATION } }
            .filter { it.isNotEmpty() }
        return words.isNotEmpty() && words.any { it in CLOSING } && words.all { it in CLOSING || it in FILLER }
    }
}
