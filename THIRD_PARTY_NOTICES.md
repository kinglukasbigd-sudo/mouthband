# Third-party notices

Everything below ships inside the MouthBand app. Full license texts are bundled in
`app/src/main/assets/licenses/` and shown in the app under Settings → Licenses.
No GPL/LGPL code is used (TarsosDSP is deliberately not used; all DSP is original Kotlin).

| Component | Use | License | Text |
|---|---|---|---|
| Fraunces (Undercase Type) | Display and heading font | SIL Open Font License 1.1 | `fraunces_ofl.txt` |
| Inter (Rasmus Andersson) | Body font | SIL Open Font License 1.1 | `inter_ofl.txt` |
| JetBrains Mono (JetBrains) | Mono labels | SIL Open Font License 1.1 | `jetbrains_mono_ofl.txt` |
| SwiftF0 model (Lars Nieradzik, lars76/swift-f0) | Pitch tracking | MIT | `swift_f0_mit.txt` |
| ONNX Runtime (Microsoft) | Runs the SwiftF0 model | MIT | `onnxruntime_mit.txt` |
| AndroidX, Jetpack Compose, Navigation, Room, DataStore, Lifecycle | App framework | Apache License 2.0 | `apache_2_0.txt` |
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization | Language runtime | Apache License 2.0 | `apache_2_0.txt` |
| Google Mobile Ads SDK, User Messaging Platform | Ads and consent | Google terms (proprietary) | `google_ads_terms.txt` |

Algorithms implemented from published descriptions (no third-party code):
- YIN pitch estimator — de Cheveigné & Kawahara, JASA 2002.
- Freeverb reverb topology — Jezar at Dreampoint (public domain).
- Note segmentation rules follow SwiftF0's `segment_notes` (MIT).

Optional instrument samples (not bundled yet, see `tools/SAMPLES.md`): FluidR3_GM.sf2 by Frank Wen, MIT.
