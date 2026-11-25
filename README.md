# Forge Imperiale — Personal Strict-Coach Native Android App

Native Kotlin + Jetpack Compose (Material 3) offline-first fitness, schedule, and Bengali home-kitchen coach built specifically for **Arnab** (`Asia/Kolkata`), styled with the **Linktree Design System** (AMOLED True Black + Chartreuse `#D2E823` / Canopy `#43E660` / Forest `#254F1A` / Dahlia `#502274` locked tuition lanes / Marigold `#F7B500` diet plates / Currant `#780016` safety STOP protocol).

## Highlights

- **Native Android Only (No Expo / RN / Flutter / WebView shell)**: `minSdk 26`, `targetSdk 35`, Single-Activity Compose, Hilt DI, Room (with exported schema + migration path), DataStore, R8 shrinking + Baseline Profile.
- **Tuition-Proof Scheduler Engine**: Locks all 12 weekly tuition blocks (`English`, `Maths`, `Science`, `SST`, `Biology`, `Bengali`) with a mandatory **15-minute buffer** before and after class, protects **8.5 hours of sleep** (never cutting below 8h), finishes Wed/Fri breakfast before `08:00` SST, places Mon/Thu light dinner at `21:45` after Maths, and locks Sunday recovery.
- **Smart Auto-Looping Exercise Streams**: Every exercise in Strength A, B, C, Cardio, Mobility, and Week 5+ gated Barbell variants ships pre-configured with verified looping streams (`Media3 ExoPlayer` clip loop + official `android-youtube-player` segment loop `[loopStartSec..loopEndSec]`), 0.5x/1x speed, mute, fullscreen, and an in-app **Video Manager**.
- **Strict Coach & Hard Safety Guardrails**: Full-screen non-bypassable **STOP IMMEDIATELY** card on pain/dizziness, Sunday rest lock, missed-workout anti-doubling logic, `>= 2` skipped meals warning, 2-week low-mood support banner, and Week 5+ barbell safety checklist (solo bench press permanently blocked).
- **Two-Way Phone Calendar & Exact Alarms**: Dedicated local `'Arnab Coach'` calendar + external exam conflict detection, `AlarmManager.setExactAndAllowWhileIdle` with screen-off rest timer alerts, Start/Snooze 10m/Skip workout notification actions, Boot/Timezone restore receiver, quiet hours during tuition/sleep, and `<= 8` notifications/day cap.
- **Offline-First + MongoDB Replica Account Sync**: All data lives locally in Room + DataStore with JSON Export/Import and native TLS MongoDB Wire Protocol (`OP_MSG` + `SCRAM-SHA-256`) cloud account backup/restore.

## Building APK on Codemagic.io (No Local Android Studio Required)

1. Push this repository to GitHub (`arnabpluxury/forge-imperiale`).
2. Sign in to [Codemagic.io](https://codemagic.io) with GitHub and add **`arnabpluxury/forge-imperiale`**.
3. *(Optional)* In Codemagic **Environment variables**, add `FORGE_MONGO_URI` with your `mongodb://...` replica URI so cloud sync is pre-filled at install time (or paste it once inside **Settings → Forge Cloud Account**).
4. Start workflow **`Forge Imperiale Android (Tests + Debug & Release APK)`** — Codemagic automatically picks up [`codemagic.yaml`](codemagic.yaml), runs `:app:testDebugUnitTest`, and produces both `app-debug.apk` and `app-release.apk` (`< 25 MB`, R8 shrunk & signed for direct phone sideloading).
