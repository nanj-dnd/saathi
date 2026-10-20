package app.saathi

import app.saathi.session.Closing
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClosingTest {
    @Test fun `no, that's all and thanks end it in every language`() {
        listOf(
            "no", "No, that's all.", "nothing else", "no thank you", "okay thanks", "nahi bas", "bas thank you",
            "नहीं", "बस", "कुछ नहीं जी", "नहीं, धन्यवाद", "ठीक है, हो गया", "बस शुक्रिया",
            "இல்லை", "போதும் நன்றி", "లేదు", "చాలు ధన్యవాదాలు", "ಇಲ್ಲ", "ಸಾಕು", "ഇല്ല", "മതി നന്ദി",
        ).forEach { assertTrue(it, Closing.isClosing(it)) }
    }

    @Test fun `a request with a no in it is still a request`() {
        listOf(
            "WhatsApp नहीं खुल रहा", "no internet", "photo nahi aa rahi", "बस का टिकट बुक करना है",
            "thank you message bhejna hai", "", "हाँ", "yes",
        ).forEach { assertFalse(it, Closing.isClosing(it)) }
    }
}
