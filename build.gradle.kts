plugins {
    // AGP 9+ fournit le support Kotlin (plus besoin du plugin kotlin.android).
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
