# DroidBridge / JavaLauncher

DroidBridge / JavaLauncher is an independent Android launcher project for users who own Minecraft: Java Edition and want to run Java Edition on Android devices.

This project is developed by **DNA Mobile Applications**. It is not affiliated with, endorsed by, sponsored by, or approved by Microsoft, Mojang, Xbox, Minecraft, PojavLauncher, Boardwalk, Amethyst, MojoLauncher, Zalith Launcher, Fold Craft Launcher, or any other third-party launcher project.

Minecraft, Microsoft, Xbox, Mojang, and related names, services, and assets belong to their respective owners.

> Microsoft authentication support in this project does not mean Microsoft, Mojang, Xbox, or Minecraft has approved, endorsed, sponsored, or reviewed this launcher.

## Current status

This repository is intended to become a complete Android launcher for Minecraft: Java Edition. Depending on the branch or release, it may include experimental, in-progress, or production-ready launcher components.

The project currently focuses on:

- Android-native launcher UI and instance management.
- Microsoft sign-in and Minecraft account verification.
- Minecraft version metadata handling.
- Version, library, asset, and runtime download flows.
- Java runtime and LWJGL integration for Android.
- Renderer/runtime compatibility work for Android devices.
- Touch, input, surface, and lifecycle bridge work required to run the game.
- Mod, modpack, resource pack, and shader pack management.

## Open-source lineage and credits

DroidBridge / JavaLauncher is a DNA Mobile Applications project, but Android Minecraft: Java Edition launchers have a long open-source history. This project may include, modify, or be informed by open-source launcher, runtime, bridge, graphics, and compatibility work from multiple projects.

Public launchers such as Amethyst and MojoLauncher openly credit their lineage and third-party components. This repository follows the same transparent approach: if this project uses, modifies, ports, studies, or derives from third-party code, that work must be credited and kept under its applicable license.

### Projects and components that may require credit

Depending on the release, branch, or build configuration, this project may use, modify, or be based in part on work from:

- **Boardwalk** - historical Minecraft: Java Edition launcher work for Android. Boardwalk source code is licensed under Apache License 2.0 unless otherwise indicated in individual files.
- **PojavLauncher** - Android/iOS Minecraft: Java Edition launcher work, including launcher-side compatibility, runtime, bridge, input, lifecycle, graphics, and native integration concepts or code. PojavLauncher is licensed under GNU LGPLv3.
- **LWJGL / LWJGL3** - Java game/native library bindings used by Minecraft and launcher runtime components.
- **OpenJDK / Java runtime builds** - Java runtime components required to run Minecraft: Java Edition.
- **Mesa 3D Graphics Library** - graphics compatibility components where bundled or used.
- **GL4ES** - OpenGL/OpenGL ES compatibility components where bundled or used.
- **AndroidX / Android platform libraries** - Android framework and support components.
- **Other renderer, native, audio, security, compatibility, or dependency components** used by specific builds.

The exact list of bundled third-party components may vary by release. See `OPEN_SOURCE_NOTICES.md`, in-app legal notices, dependency metadata, and individual file headers for details.

## License notice

Files written entirely by DNA Mobile Applications may be licensed separately by DNA Mobile Applications.

Files derived from, copied from, modified from, or based on third-party projects remain subject to their original licenses and notices. Do not remove copyright headers, license headers, author notices, or attribution from third-party-derived files.

If this repository includes LGPL-covered PojavLauncher-derived code, you must comply with the LGPL terms for those portions, including preserving notices and making the required source code and modifications available.

If this repository includes Apache-2.0-covered Boardwalk-derived code, you must preserve required notices and provide attribution as required by the license and file headers.

Before publishing binaries, make sure the app includes or links to:

- the app privacy policy;
- the app terms of service;
- open-source notices;
- license texts for third-party components;
- source-code links required by applicable licenses; and
- any notices required by files copied or modified from third-party projects.

## Privacy

DroidBridge / JavaLauncher is designed to avoid operating a DNA Mobile Applications account server. Microsoft login is handled through Microsoft services. Login session data, Minecraft profile data, launcher settings, logs, worlds, mods, and launcher files are intended to remain local to the user’s device unless the user chooses to share, export, upload, or send them through another service.

See `PRIVACY_POLICY.md` for the full privacy policy.

## Microsoft login configuration

Set these values in either `~/.gradle/gradle.properties` or the project `gradle.properties` file:

```properties
MICROSOFT_CLIENT_ID=your-app-client-id
MICROSOFT_REDIRECT_URI=ca.dnamobile.javalauncher:/oauth2redirect
MICROSOFT_SCOPE=XboxLive.signin offline_access openid profile email
```

Do not commit private secrets, signing keys, production keystores, API keys, or private OAuth credentials to the repository.

## Example launch flow

A normal launcher flow may include:

1. User signs in with Microsoft.
2. The app authenticates with Microsoft, Xbox Live, XSTS, and Minecraft services.
3. The app verifies Minecraft profile/access where required.
4. The app refreshes the Minecraft version list.
5. User creates or selects an instance.
6. The app downloads version metadata, libraries, assets, runtime files, and native components.
7. The app builds a launch plan.
8. The runtime/bridge layer starts Minecraft with the selected renderer, Java runtime, and instance configuration.

## Planned production pieces

A production-ready launcher should include:

- full Microsoft -> Xbox Live -> XSTS -> Minecraft services auth chain;
- secure local token storage and sign-out cleanup;
- version JSON, client JAR, library, asset index, and asset object download handling;
- virtual assets and legacy asset handling;
- Java runtime installer and runtime selector;
- native library extraction and validation;
- custom LWJGL loading and renderer selection;
- process launch / bootstrap execution;
- input bridge, touch controls, keyboard/mouse/controller support;
- foreground installation service for long downloads;
- crash handling, logs, lifecycle recovery, and user-facing diagnostics;
- mod/modpack/resource pack/shader pack management; and
- legal, privacy, and open-source notice screens in the app.

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
- Confirm that Microsoft login configuration matches the approved app registration and redirect URI.

## Contributing

Contributions are welcome if they respect the project’s legal and technical boundaries.

Please do not submit code copied from another launcher or project unless the license permits it and attribution is preserved. Pull requests that include third-party-derived code should clearly identify the source project, original license, files changed, and any required notices.

## Legal disclaimer

This README is not legal advice. Before commercial distribution or app-store publication, review all third-party licenses, Microsoft/Minecraft terms, Google Play policies, and any store-specific requirements that apply to your release.
