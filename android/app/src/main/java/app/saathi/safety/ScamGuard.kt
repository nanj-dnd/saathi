package app.saathi.safety

/**
 * On-device checks that run even when Saathi is idle. Remote-control apps are the tool behind most
 * "bank KYC" and "digital arrest" frauds against seniors: the caller asks them to install one, then
 * watches the OTP arrive. Opening one gets a spoken warning, at most once per app every 10 minutes.
 * Everything subtler (collect requests, threats on screen) is left to the model, which sees the screen.
 */
class ScamGuard(private val now: () -> Long = System::currentTimeMillis) {
    companion object {
        val REMOTE_CONTROL = setOf(
            "com.anydesk.anydeskandroid",
            "com.teamviewer.quicksupport.market",
            "com.teamviewer.teamviewer.market.mobile",
            "com.teamviewer.host.market",
            "com.rustdesk.rustdesk",
            "com.carriez.flutter_hbb", // RustDesk
            "com.sand.airdroid",
            "com.sand.airmirror",
            "com.airdroid.biz",
            "com.splashtop.remote.pad.v2",
            "com.realvnc.viewer.android",
            "com.microsoft.rdc.androidx",
            "com.zoho.assist.agent",
        )
        const val COOLDOWN_MS = 10 * 60 * 1000L
    }

    private val lastWarned = HashMap<String, Long>()

    /** True if opening [pkg] deserves a warning right now. */
    fun shouldWarn(pkg: String): Boolean {
        if (pkg !in REMOTE_CONTROL) return false
        val t = now()
        val last = lastWarned[pkg]
        if (last != null && t - last < COOLDOWN_MS) return false
        lastWarned[pkg] = t
        return true
    }
}
