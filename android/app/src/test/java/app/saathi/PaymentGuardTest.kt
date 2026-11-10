package app.saathi

import app.saathi.safety.PaymentGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentGuardTest {
    @Test fun `payment apps are recognised by package`() {
        for (pkg in listOf("com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp")) {
            assertTrue(pkg, PaymentGuard.isPaymentApp(pkg))
        }
        for (pkg in listOf("com.grofers.customerapp", "com.whatsapp", "com.android.settings", null)) {
            assertFalse("$pkg", PaymentGuard.isPaymentApp(pkg))
        }
    }

    @Test fun `a shop's checkout and a PIN pad are payment pages`() {
        assertTrue(PaymentGuard.isPaymentPage(listOf("Checkout", "Delivering to Home", "₹155 TOTAL", "Place Order")))
        assertTrue(PaymentGuard.isPaymentPage(listOf("ENTER UPI PIN", "1", "2", "3")))
        assertTrue(PaymentGuard.isPaymentPage(listOf("Card number", "Expiry", "CVV")))
        assertTrue(PaymentGuard.isPaymentPage(listOf("भुगतान करें")))
    }

    @Test fun `ordinary screens, including a cart before checkout, are not`() {
        assertFalse(PaymentGuard.isPaymentPage(listOf("Search 'milk'", "Amul Taaza Milk 500 ml", "ADD", "View cart")))
        assertFalse(PaymentGuard.isPaymentPage(listOf("Display", "Font size and style", "Screen zoom")))
        assertFalse(PaymentGuard.isPaymentPage(listOf("PhonePe", "Recharge", "Paytm")))
    }
}
