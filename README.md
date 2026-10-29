# DeLauncher

DeLauncher is an independent, open-source minimalist Android home launcher inspired by one-handed, list-based launchers. It does not include Niagara Launcher code or assets.

## Features

- Register as an Android Home launcher.
- Discover and launch installed apps with their real application icons.
- Alphabetical app list and right-edge alphabet scrubber (drag or tap a letter).
- Search installed apps.
- Editable favourites: add/remove apps and move favourites up/down; saved locally.
- Local light/dark theme and four accent colors.
- **Third-party Android widgets:** use Android's system widget picker to add any compatible installed app widget to the top area, including widgets that require configuration; remove the active widget in settings.
- GitHub Actions workflow builds a debug APK on pushes and manual dispatch.

## Build on GitHub (no Android Studio required)

1. Create a new GitHub repository named `DeLauncher`.
2. Upload the contents of this project to the repository root (so `settings.gradle.kts`, `app/`, and `.github/` are at the root).
3. Open **Actions** and allow workflows if GitHub asks.
4. Run **Build DeLauncher APK**, or push a commit to `main`/`master`.
5. Open the completed workflow run, scroll to **Artifacts**, and download `delauncher-debug-apk`.
6. Extract the downloaded artifact ZIP; install `app-debug.apk` on your Android device. You may need to allow installs from your browser/files app.

The APK is a debug build, suitable for personal testing. A signed release build requires a signing key and GitHub secrets, which are not configured here.

## Build locally

Open in Android Studio and build `app`'s `assembleDebug` task. The APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Widget notes

DeLauncher uses Android's `AppWidgetHost` and the system widget picker. Widgets are implemented by their provider apps, so appearance, interaction, refresh behavior, resizing, and configuration depend on the provider and Android version. This initial implementation hosts one top-area widget at a time.

## License

GPL-3.0-only. See `LICENSE`.
