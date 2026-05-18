package net.dkly.myflashlight

enum class FlashlightMode {
    STEADY,
    STROBE,
    SOS;

    companion object {
        fun fromName(name: String?): FlashlightMode =
            values().firstOrNull { it.name == name } ?: STEADY
    }
}
