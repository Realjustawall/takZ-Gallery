# TakZ Gallery (گالری تکز)

**TakZ Gallery** is a complete, production-ready, 100% offline Android photo and video gallery engineered with Kotlin, Jetpack Compose (Material 3), Room, and an integrated on-device bilingual (Persian `fa` & English `en`) AI inference suite.

---

## 1. Core Architectural Philosophy & Privacy Guarantee

- **100% Offline AI**: Zero cloud APIs, zero external inference endpoints, and zero first-launch model downloads. All quantized `TFL3` INT8 model weights and bilingual vocabularies are bundled in `app/src/main/assets/models/`.
- **Zero Internet Permission**: The core manifest (`AndroidManifest.xml`) requests **no `android.permission.INTERNET`**, guaranteeing that user media and metadata never leave the device.
- **Native Persian & English Support**:
  - Bundled **Vazirmatn** (`res/font/vazirmatn.ttf`), **Plus Jakarta Sans**, and **JetBrains Mono** under SIL OFL-1.1.
  - Instant RTL (`fa`) and LTR (`en`) switching, Persian/Arabic character normalization (`ك` -> `ک`, `ي` -> `ی`, ZWNJ handling, Eastern/Western digits), and **Solar Hijri (Jalali / جلالی)** & **Gregorian** calendar formatting.

---

## 2. On-Device AI Engine Modules (`com.example.core.ai`)

| Engine | Bundled Asset | Description |
| :--- | :--- | :--- |
| `ImageEmbeddingEngine` | `takz_vision_embedder_int8.tflite` | Extracts 64-D multi-scale chromatic, spatial, texture, and structural features projected via INT8 quantized weights onto the unit hypersphere. |
| `TextEmbeddingEngine` | `takz_multilingual_text_fa_en_int8.tflite` & `vocab_fa_en.json` | Encodes natural-language queries in both Persian (`ماشین قرمز کنار درخت`) and English (`A red car parked beside a tree`) into the shared 64-D vector space without external translation APIs. |
| `ImageTaggingEngine` | Integrated | Generates multi-subject bilingual tags with confidence scores (`[0.55..0.99]`) while preserving user-defined manual tags separately across re-indexing. |
| `OCRRecognitionEngine` | `takz_ocr_fa_en_int8.tflite` | Extracts Persian and English text, OTP verification codes (`4..8` digits), URLs, emails, phone numbers, dates, and mathematical formulas. |
| `ImageSegmentationEngine` | `takz_foreground_segmentation_int8.tflite` | Performs offline foreground subject segmentation with bilateral edge feathering, smoothing, manual erase/restore brushes, and transparent PNG/WebP export. |
| `SimilarityEngine` | Integrated | Computes cryptographic `SHA-256` hashes for exact duplicates and 64-bit Perceptual Difference Hashes (`dHash`) + 64-D cosine similarity for near-duplicates. |
| `ImageQualityEngine` | Integrated | Estimates Laplacian variance sharpness and resolution scores to recommend the best photograph in duplicate clusters. |
| `SearchRankingEngine` | Integrated | Combines semantic cosine similarity, OCR matches, bilingual tags, and 15+ structured filters with visual match badges. |

---

## 3. Reproducible Model Export & Build Instructions

### Regenerating Quantized Models
```bash
python3 scripts/export_quantized_models.py
```

### Building & Running Tests
```bash
# Run Unit & Robolectric Integration Tests
gradle :app:testDebugUnitTest

# Assemble Debug APK
gradle :app:assembleDebug
```

---

## 4. Licenses & Attribution

- **Models (`app/src/main/assets/licenses/MODEL_LICENSES.txt`)**: Apache License 2.0.
- **Typography (`app/src/main/assets/licenses/FONT_LICENSES.txt`)**: Vazirmatn by Saber Rastikerdar, Plus Jakarta Sans by Tokotype, JetBrains Mono by JetBrains (SIL Open Font License 1.1).
