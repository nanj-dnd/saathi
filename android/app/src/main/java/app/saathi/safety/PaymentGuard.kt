package app.saathi.safety

import java.util.Locale

/**
 * Where Bob gets out of the way entirely: payment apps, and payment pages inside any other app
 * (a shop's checkout, a UPI PIN pad, a card form). On these the face, the caption and the hand all
 * disappear and guidance stops; they come back when the senior leaves. Bob never points at, reads
 * out or sits over anything to do with her money.
 */
object PaymentGuard {
    /** Apps whose every screen is about money. */
    val PAYMENT_APPS = setOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "com.phonepe.app",
        "net.one97.paytm",
        "in.org.npci.upiapp", // BHIM
        "com.dreamplug.androidapp", // CRED
        "com.mobikwik_new",
        "com.freecharge.android",
        "com.google.android.apps.walletnfcrel", // Google Wallet
        "com.sbi.lotusintouch", // SBI YONO
        "com.sbi.SBIFreedomPlus",
        "com.csam.icici.bank.imobile",
        "com.snapwork.hdfc",
        "com.hdfcbank.payzapp",
        "com.axis.mobile",
        "com.msf.kbank.mobile", // Kotak
        "com.bankofbaroda.mconnect",
        "com.infrasofttech.indianbank",
        "com.lcode.pnb", // PNB
    )

    /**
     * Words that only appear on a payment step: a checkout's final button, a PIN pad, a card form.
     * Looked up with Android's own text search, so they're matched case-insensitively inside labels.
     */
    val PAGE_CUES = listOf(
        "Place Order", "Proceed to pay", "Proceed To Pay", "Pay now", "Confirm payment", "Complete payment",
        "Enter UPI PIN", "UPI PIN", "Card number", "CVV", "Pay securely",
        "भुगतान करें", "पेमेंट करें", "यूपीआई पिन",
    )

    fun isPaymentApp(pkg: String?): Boolean = pkg != null && pkg in PAYMENT_APPS

    /** True if any of [labels] (what is on screen) is a payment step. For tests and the tree path. */
    fun isPaymentPage(labels: List<String>): Boolean {
        val have = labels.map { it.lowercase(Locale.ROOT) }
        return PAGE_CUES.any { cue -> val c = cue.lowercase(Locale.ROOT); have.any { c in it } }
    }
}
