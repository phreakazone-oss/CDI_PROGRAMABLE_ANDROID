plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "id.ns200.cdir7"
    compileSdk = 35

    defaultConfig {
        applicationId = "id.ns200.cdir7"
        minSdk = 29
        targetSdk = 35
        versionCode = 8
        versionName = "7.1-usb-dfu-dashboard"
    }
}
