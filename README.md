<p align="center">
  <img src="docs/app_icon.png" alt="DroidBridge / JavaLauncher app icon" width="128" height="128">
</p>

<h1 align="center">DroidBridge / JavaLauncher</h1>

<p align="center">
  An independent Android launcher project for users who own Minecraft: Java Edition and want to run Java Edition on Android devices.
</p>

<p align="center">
  <strong>NOT AN OFFICIAL MINECRAFT PRODUCT.</strong><br>
  <strong>NOT APPROVED BY OR ASSOCIATED WITH MOJANG, MICROSOFT, OR XBOX.</strong>
</p>

---

## About

DroidBridge / JavaLauncher is developed by **DNA Mobile Applications** as an independent Android launcher project.

This project is not affiliated with, endorsed by, sponsored by, or approved by Microsoft, Mojang, Xbox, Minecraft, PojavLauncher, Boardwalk, Amethyst, MojoLauncher, Zalith Launcher, Fold Craft Launcher, or any other third-party launcher project.

Minecraft, Microsoft, Xbox, Mojang, and related names, services, trademarks, and assets belong to their respective owners.

Microsoft authentication support, if present in a private or release build, does **not** mean Microsoft, Mojang, Xbox, or Minecraft has approved, endorsed, sponsored, or reviewed this launcher.

---

## App icon in this README

The app icon shown at the top of this README expects this file to exist in the repository:

```text
docs/app_icon.png
```

Before publishing, copy your launcher icon into that path. For example, you can export or copy your Android launcher icon into `docs/app_icon.png` so GitHub displays it at the top of the README.

---

## Current status

This repository is intended to become a complete Android launcher framework for Minecraft: Java Edition on Android. Depending on the branch, release, or build configuration, it may include experimental, in-progress, or production-ready launcher components.

The project currently focuses on:

- Android-native launcher UI and instance management.
- Account, profile, and local launcher state management.
- Version metadata handling.
- Java runtime and LWJGL integration for Android.
- Renderer/runtime compatibility work for Android devices.
- Touch, input, surface, and lifecycle bridge work required to run Java games on Android.
- Mod, modpack, resource pack, and shader pack management.
- Legal, privacy, and open-source notice screens.

---

## Public source release notice

This public source release may intentionally exclude private implementation classes, app credentials, signing configuration, account/authentication internals, game installation internals, and production launcher execution logic.

The public source tree should not include:

- private OAuth credentials;
- production signing keys or keystores;
- private API keys;
- bundled Minecraft game files, assets, libraries, or proprietary content;
- user account tokens or session data;
- private installer implementation classes; or
- private release-only launcher implementation classes.

Users are responsible for owning Minecraft: Java Edition and complying with the Minecraft EULA, Microsoft Services Agreement, Minecraft Usage Guidelines, and any other applicable terms.

---

## Open-source lineage and credits

DroidBridge / JavaLauncher is a DNA Mobile Applications project, but Android Minecraft: Java Edition launchers have a long open-source history. Some launcher-side logic, compatibility ideas, runtime integration patterns, bridge behavior, input/surface handling, or implementation details in this project may be based on, adapted from, studied from, or inspired by the projects listed below.

Where code is copied, modified, ported, or derived from another project, the original copyright notices, license headers, and attribution must be preserved.

### Boardwalk

- Original repository: [zhuowei/Boardwalk](https://github.com/zhuowei/Boardwalk)
- Project description: Boardwalk is a historical Minecraft: Java Edition launcher for Android.
- License: Apache License 2.0 unless otherwise indicated by individual files.

Boardwalk is credited for early Android Minecraft: Java Edition launcher work and for historical launcher/runtime concepts that influenced later Android Java launcher projects.

### PojavLauncher

- Original Android repository: [PojavLauncherTeam/PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)
- Project description: PojavLauncher is an Android/iOS Minecraft: Java Edition launcher based on Boardwalk.
- License: GNU LGPLv3 according to the repository license/readme. Always verify the current license and individual file headers before reusing code.

PojavLauncher is credited for Android launcher-side compatibility work, runtime integration ideas, bridge/input/lifecycle concepts, renderer and native integration approaches, and other launcher logic that may have informed or been adapted into this project.

### Important attribution rule

This project must not remove or hide third-party attribution. If a file, method, class, asset, script, native component, or build process is copied from or derived from Boardwalk, PojavLauncher, or another project, keep the original notices and document the source in `OPEN_SOURCE_NOTICES.md`.

---

## Other third-party components that may require credit

Depending on the release, branch, or build configuration, this project may use, modify, bundle, or depend on additional third-party work, including but not limited to:

- [LWJGL / LWJGL3](https://github.com/LWJGL/lwjgl3)
- OpenJDK / Java runtime builds
- Mesa 3D Graphics Library
- GL4ES
- AndroidX / Android platform libraries
- renderer, native, audio, security, compatibility, and dependency components used by specific builds

The exact list of bundled third-party components may vary by release. See `OPEN_SOURCE_NOTICES.md`, in-app legal notices, dependency metadata, and individual file headers for details.

---

## License notice

Files written entirely by DNA Mobile Applications may be licensed separately by DNA Mobile Applications.

Files derived from, copied from, modified from, or based on third-party projects remain subject to their original licenses and notices. Do not remove copyright headers, license headers, author notices, or attribution from third-party-derived files.

If this repository includes PojavLauncher-derived code, you must comply with the applicable PojavLauncher license terms for those portions, including preserving notices and making the required source code and modifications available when required.

If this repository includes Boardwalk-derived code, you must comply with the Apache License 2.0 terms for those portions, including preserving notices and providing required attribution.

Before publishing binaries, make sure the app includes or links to:

- the app privacy policy;
- the app terms of service;
- open-source notices;
- license texts for third-party components;
- source-code links required by applicable licenses; and
- any notices required by files copied or modified from third-party projects.

---

## Privacy

DroidBridge / JavaLauncher is designed to avoid operating a DNA Mobile Applications account server.

Microsoft login, where available, is handled through Microsoft services. Login session data, Minecraft profile data, launcher settings, logs, worlds, mods, and launcher files are intended to remain local to the user’s device unless the user chooses to share, export, upload, or send them through another service.

See `PRIVACY_POLICY.md` for the full privacy policy.

---

## Build configuration

Do not commit private secrets, signing keys, production keystores, API keys, OAuth credentials, local tokens, or private release configuration to the repository.

Recommended private configuration files to keep out of Git:

```text
local.properties
keystore.properties
signing.properties
secrets.properties
.env
*.jks
*.keystore
```

If a private/full build uses Microsoft login, configure OAuth values through private Gradle properties, local environment variables, or another secure build-time configuration method. Do not publish production credentials in the public repository.

---

## Building

Open the project in Android Studio and let Gradle sync.

Typical local build commands:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

On Windows, use:

```bat
gradlew.bat assembleDebug
gradlew.bat assembleRelease
```

Release builds should be signed with the correct production keystore and should not include debug-only OAuth redirects, debug application IDs, test API keys, or private development configuration.

---

## Google Play and store publication checklist

Before publishing on Google Play or another app store:

- Ensure the app name, icon, screenshots, and description do not imply official Microsoft, Mojang, Xbox, or Minecraft endorsement.
- Include a publicly accessible privacy policy URL.
- Make the in-app privacy policy and legal notices easy to find.
- Complete the Google Play Data safety form accurately.
- Disclose any data accessed, collected, transmitted, or shared by the app and by third-party SDKs/libraries.
- Include open-source notices and license texts.
- Provide source-code links required by LGPL/GPL or other applicable licenses.
- Review third-party APIs, SDKs, and content services used by the app.
- Confirm that Microsoft login configuration matches the approved app registration and redirect URI for any private/full release build.

---

## Contributing

Contributions are welcome if they respect the project’s legal and technical boundaries.

Please do not submit code copied from another launcher or project unless the license permits it and attribution is preserved. Pull requests that include third-party-derived code should clearly identify:

- the source project;
- the original license;
- the original file or commit if known;
- the files changed in this project; and
- any required notices.

---

## Legal disclaimer

This README is not legal advice. Before commercial distribution or app-store publication, review all third-party licenses, Microsoft/Minecraft terms, Google Play policies, and any store-specific requirements that apply to your release.
