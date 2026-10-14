package app.saathi.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Mirrors worker/src/schema.ts.

@Serializable
data class PastStep(
    val say: String,
    val label: String,
    val status: String,
    val app: String,
)

@Serializable
data class TurnRequest(
    val language: String,
    /** What the senior said, transcribed on the phone. Null on auto-advance turns. */
    val utterance: String? = null,
    val goal: String,
    val app: String,
    val playbook: String,
    @SerialName("screenshot_b64") val screenshotB64: String? = null,
    @SerialName("screen_w") val screenW: Int,
    @SerialName("screen_h") val screenH: Int,
    val tree: String,
    @SerialName("secure_screen") val secureScreen: Boolean,
    val history: List<PastStep>,
)

@Serializable
data class Guide(
    val say: String,
    val goal: String = "",
    @SerialName("target_kind") val targetKind: String = "none",
    @SerialName("node_id") val nodeId: String = "",
    val x: Double = -1.0,
    val y: Double = -1.0,
    val label: String = "",
    val status: String = Status.NEXT_STEP,
    val danger: String = Danger.NONE,
    val expect: String = "",
)

object Status {
    const val NEXT_STEP = "next_step"
    const val DONE = "done"
    const val NEED_INFO = "need_info"
    const val USER_MUST_DO = "user_must_do"
    const val CANNOT_HELP = "cannot_help"
}

object Danger {
    const val NONE = "none"
    const val SCAM = "scam_suspected"
    const val SENSITIVE = "sensitive_screen"
}

@Serializable
data class TurnResponse(
    val guide: Guide,
    @SerialName("timings_ms") val timingsMs: Map<String, Long> = emptyMap(),
)

@Serializable
data class RegisterResponse(val token: String)
