package com.mouthband.ui.licenses

import androidx.annotation.StringRes
import com.mouthband.R

/** A bundled third-party component and the license text shipped in assets/licenses. */
data class LicenseEntry(@StringRes val name: Int, @StringRes val license: Int, val asset: String)

/** Everything that ships inside the APK (mirrors THIRD_PARTY_NOTICES.md). */
val BundledLicenses = listOf(
    LicenseEntry(R.string.lic_fraunces, R.string.lic_ofl, "licenses/fraunces_ofl.txt"),
    LicenseEntry(R.string.lic_inter, R.string.lic_ofl, "licenses/inter_ofl.txt"),
    LicenseEntry(R.string.lic_jetbrains, R.string.lic_ofl, "licenses/jetbrains_mono_ofl.txt"),
    LicenseEntry(R.string.lic_swiftf0, R.string.lic_mit, "licenses/swift_f0_mit.txt"),
    LicenseEntry(R.string.lic_onnx, R.string.lic_mit, "licenses/onnxruntime_mit.txt"),
    LicenseEntry(R.string.lic_jetpack, R.string.lic_apache, "licenses/apache_2_0.txt"),
    LicenseEntry(R.string.lic_google_ads, R.string.lic_google_terms, "licenses/google_ads_terms.txt"),
)
