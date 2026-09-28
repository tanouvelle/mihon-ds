plugins {
    alias(mihonx.plugins.android.library)
}

android {
    namespace = "eu.davidea.flexibleadapter"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "VERSION_NAME", "\"5.1.0\"")
    }
}

dependencies {
    // Match the original module's compile dependency. The app resolves its own newer version.
    implementation("androidx.recyclerview:recyclerview:1.1.0")
}
