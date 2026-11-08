package com.pushupanalyzer.app

/** Minimum observed excursion, independent of the camera's projected absolute angle. */
enum class DetectionSensitivity(val angleExcursion: Double, val reachExcursion: Double, val recovery: Double) {
    STANDARD(22.0, 0.16, 0.80),
    SENSITIVE(12.0, 0.08, 0.70),
    VERY_SENSITIVE(8.0, 0.05, 0.60);

    companion object {
        fun fromName(value: String?): DetectionSensitivity =
            entries.firstOrNull { it.name == value } ?: SENSITIVE
    }
}
