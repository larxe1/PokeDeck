# Pokémon GO Raid Overlay — Phase 1 Design Specification

**Date:** 2026-11-22  
**Package:** `com.pogooverlay.app`  
**Platform:** Android (minSdk 29 / Android 10, targetSdk 35, compileSdk 35)

---

## 1. Intent & Why

Pokémon GO raid lobbies and boss encounters give players limited time to identify optimal counter types, especially for bosses with dual typings, Mega evolutions, Primal reversions, or regional/alternate forms whose weaknesses differ dramatically from base forms (e.g., 2.56× double weaknesses).

**PoGo Raid Overlay** is an Android-only floating overlay that sits cleanly above Pokémon GO and displays raid boss weaknesses **left-to-right by priority** (`2.56×` double weaknesses first, followed by `1.6×` single weaknesses).

The architecture is built around a modular screen-reader and detector pipeline (`core/capture`, `core/ocr`, `core/detector`) so that Phase 2+ features—such as **My Pokémon** storage/IV scanning and **Team GO Rocket Grunt** line decoding—can plug directly into the same `ScreenDetector<T>` abstraction without rewriting overlay, capture, or OCR plumbing.

---

## 2. Goals, Non-Goals & Anti-Goals

### Goals
- **Instant Raid Boss Weakness Lookup:** Automatically detect raid bosses from the Pokémon GO screen via MediaProjection + on-device OCR, or allow 1-tap manual scan, live raid list browsing, instant species search, and manual dual-type calculation.
- **Accurate Pokémon GO Multipliers:** Use the exact Pokémon GO type effectiveness multipliers (`2.56×`, `1.6×`, `0.625×`, `0.39×`, `0.244×`) rather than mainline series multipliers.
- **Form-Aware Resolution:** Preserve type-changing forms (`Mega`, `X`/`Y`, `Primal`, `Alolan`, `Galarian`, `Hisuian`, `Paldean`, `Origin`, `Altered`, `Dusk Mane`, `Dawn Wings`, `Crowned Sword`/`Shield`) while stripping cosmetic/encounter prefixes, plus provide an inline `[Form ▾]` quick switcher.
- **Non-Intrusive Floating UX:** Unobtrusive draggable edge pill with battery-conscious Auto ON/OFF toggle, 1-tap manual scan, Quick Drawer, and a stay-open-until-dismissed top weakness banner.
- **Offline-First Data & Pixel Sprites:** Bundle PvPoke species data, ScrapedDuck raid fallback JSON, and offline PokéSprite/PokéAPI pixel sprite PNGs (`assets/sprites/*.png`) in APK assets, with live `raids.min.json` refresh when online.

### Non-Goals (Phase 1)
- No iOS support.
- No **My Pokémon** storage, bag, or IV scanning in Phase 1.
- No **Team GO Rocket** Grunt auto-OCR decoding in Phase 1.
- No inspection of Pokémon GO process memory, private app storage, or network traffic (strictly screen-capture + OCR + public static/community JSON feeds).

### Anti-Goals
- **Never** use mainline Pokémon type multipliers (`4×`, `2×`, `0.5×`, `0.25×`, `0×`).
- **Never** block, obscure, or intercept taps on Pokémon GO's **Battle**, **Ready**, or lobby action buttons.

---

## 3. Technical Approach

- **Language & UI:** Native Android using **Kotlin 2.0.21**, **Jetpack Compose** (Compose BOM `2024.10.01`, Material 3), Coroutines (`1.9.0`), and `kotlinx-serialization-json` (`1.7.3`).
- **On-Device OCR:** Google ML Kit offline text recognition (`com.google.mlkit:text-recognition:16.0.1`) — zero network latency, works offline during raids.
- **Screen Capture:** `MediaProjection` + `VirtualDisplay` + `ImageReader` running inside a `mediaProjection` foreground service (`OverlayService`).
- **Type Effectiveness Engine:** Hardcoded Pokémon GO 18×18 type chart with exact Go multipliers:
  - Super Effective (double): `2.56×` (`1.6 * 1.6`)
  - Super Effective (single): `1.6×`
  - Neutral: `1.0×`
  - Not Very Effective (single): `0.625×`
  - Double Resistance: `0.39×` (`0.625 * 0.625`)
  - Immunity-tier Resistance (Go): `0.244×` (`0.625 * 0.390625`)
- **Data & Offline Sprite Assets:**
  - Bundled `app/src/main/assets/pokemon_db.json` generated from PvPoke `gamemaster/pokemon.json` with `spriteFile` references.
  - Bundled offline pixel sprites in `app/src/main/assets/sprites/*.png` (PokéSprite Gen 8 + PokéAPI fallback) cached in memory via `SpriteAssetCache` (`LruCache`) and rendered sharp with `FilterQuality.None` (plus a Canvas Pokéball fallback).
  - Bundled `app/src/main/assets/raids_fallback.json` + live refresh from ScrapedDuck `raids.min.json`.
- **Build & CI Tooling:** Gradle `8.9`, AGP `8.7.2`, OpenJDK `17`, lightweight local Android SDK (`platforms;android-35`, `build-tools;35.0.0`, `platform-tools`), and GitHub Actions workflow building and uploading `app-debug.apk`.

---

## 4. Modular Boundaries

| Module / Package | Responsibility |
|---|---|
| `core/data` | Pokémon GO 18×18 type chart & weakness calculator, species repository (`pokemon_db.json`), raid rotation repository (`raids_fallback.json` + live ScrapedDuck refresh), form/prefix normalization, shared `OverlayStateHolder`. |
| `core/capture` | `MediaProjection` session management, `VirtualDisplay` / `ImageReader` frame acquisition, throttled bitmap extraction and crop regions. |
| `core/ocr` | ML Kit `TextRecognizer` wrapper returning structured text blocks/lines with bounding boxes and confidence heuristics. |
| `core/detector` | Generic `ScreenDetector<T>` contract and Phase 1 `RaidBossDetector` implementation (matches OCR lines against species/forms + raid tier cues; extensible for Phase 2+ detectors). |
| `overlay` | `OverlayService` (foreground `mediaProjection` service), `WindowManager` view hosting, `OverlayLifecycleOwner` (bridging Compose `LifecycleOwner`, `ViewModelStoreOwner`, `SavedStateRegistryOwner` for floating windows), floating edge pill, Quick Drawer, and Priority Weakness Banner. |
| `ui` | `ui/theme` (`PoGoOverlayTheme` Light Pokédex tokens), `ui/components` (`PokemonSprite`, `TypeBadge`, `WeaknessPriorityStrip`), `ui/home` (`HomeScreen` hero + curved sheet + bottom CTA), and `ui/MainActivity`. |

---

## 5. Name Normalization & Prefix Rules

OCR output from raid eggs, raid lobbies, and boss banners often includes encounter modifiers alongside form qualifiers:

- **Strip dynamically (do not change typing):**
  - `Shadow`, `Purified`, `Dynamax`, `Gigantamax`, `G-Max`, `D-Max`, `Apex`
- **Preserve type-changing / species-distinctive forms:**
  - `Mega`, `X` / `Y` (e.g., `Mega Charizard X`, `Mega Charizard Y`, `Mega Mewtwo X`/`Y`)
  - `Primal` (`Primal Kyogre`, `Primal Groudon`)
  - Regional variants: `Alolan`, `Galarian`, `Hisuian`, `Paldean`
  - Formes/fusions: `Origin`, `Altered`, `Dusk Mane`, `Dawn Wings`, `Crowned Sword`, `Crowned Shield`
- **Quick Switcher (`[Form ▾]`):**
  - When a matched species has alternate forms (or OCR misses a form qualifier like `Alolan` or `Mega X`), the banner exposes a compact `[Form ▾]` chip allowing instant 1-tap switching without re-scanning.

---

## 6. Visual & UX Direction (Modern Light Pokédex)

- **Light Pokédex Palette & Typography:** Soft cool `#F7F8FA` background, crisp pure-white `#FFFFFF` elevated cards (`20dp`–`28dp` corners), `#ECEFF4` borders/dividers, deep charcoal `#1A1C20` ink, muted slate `#6E7582`, and Pikachu Golden-Yellow `#F7CF31` default hero header (smoothly animating via `animateColorAsState` to a previewed boss's primary type color).
- **Priority Weakness Strip:** Displays `2.56×` double weaknesses left-to-right first, followed by `1.6×` single weaknesses. Resistances ("Avoid": `0.244×`, `0.39×`, `0.625×`) stay collapsed behind `"Show Avoid / Resistances ▾"` by default so counter types remain scannable at a glance.

1. **App Launch (`MainActivity` & `HomeScreen`):**
   - Top curved type-colored Hero Header with subtle Pokéball watermark showing `"Raid Counter HUD Ready"` or the selected raid/Pokédex boss's pixel sprite, `#dex`, `TypeChip`s, and inline `WeaknessPriorityStrip`.
   - Overlapping pure-white top-rounded sheet (`28dp`) with minimalist `#1A1C20` underline tabs:
     - **`Today's Raids`:** Pill search bar filtering active raids or full offline Pokédex, refresh button, and boss cards (`PokemonSprite`, tier pill, CP range, `TypeDot`s) with tap-to-preview in the hero.
     - **`Setup & Status`:** Bordered status cards for `Draw Over Other Apps`, `Notifications` (Android 13+), `Offline Pokédex & Pixel Sprites`, and `Live Raid Feed`.
   - **Bottom-Anchored CTA Bar:** Full-width charcoal-black `#1A1C20` **"Activate Overlay"** pill (switching to coral `#E5484D` **"Stop Overlay"** when active) plus a subtle `"Start Manual-Only Mode"` option when stopped.
2. **Floating Edge Pill:**
   - Pure-white draggable capsule (`FLAG_NOT_FOCUSABLE`) along screen edges so it never blocks Pokémon GO's Battle/Ready buttons.
   - Controls: `[Auto ●]` / `[Saver]` scan mode toggle, `[📷]` 1-tap scan fallback, and `[≡]` Quick Drawer toggle (Quick Drawer includes `Raids`, focusable `Search`, `Type Calc`, and `"Stop Overlay"`).
3. **Priority Weakness Banner:**
   - Top-anchored pure-white floating card (`20dp`) with a slim type-tinted header band showing `PokemonSprite`, boss title, optional prefix pill, `[Form ▾]` switcher, and `✕` dismiss button, plus `WeaknessPriorityStrip` on the white card body.
   - **Stay-Open Behavior:** Remains open until the user explicitly dismisses it (`✕` button or horizontal swipe gesture) or a different boss is detected (no auto-hide timer), ensuring counters stay visible throughout lobby prep and team selection.
