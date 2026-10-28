# Miogram

Miogram is an unofficial Telegram client for Android (12 and newer), written in Kotlin with
Jetpack Compose and Material 3 Expressive. This repository receives one commit per release.

## Building

Miogram talks to Telegram through ktproto, an MTProto 2.0 library in pure Kotlin. The library is
not public yet, so this repository does not build on its own: `settings.gradle.kts` includes it
from `../ktproto` with `includeBuild("../ktproto")`.

Requirements: JDK 17, the Android SDK (platform 37, NDK 29.0.14206865, CMake 3.22.1) and Rust
with the Android targets for the lottie renderer:

```sh
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android i686-linux-android
git clone --recursive <this repository>
```

Get an api id and hash at https://my.telegram.org and put them in `local.properties`:

```properties
sdk.dir=/path/to/Android/sdk
telegram.apiId=12345
telegram.apiHash=0123456789abcdef0123456789abcdef
# optional: a Google Maps key for the map screens
maps.apiKey=
```

Then build:

```sh
./gradlew :app:assembleSandboxDebug   # Telegram's test servers
./gradlew :app:assembleProdDebug      # production servers
```

The `prod` flavour can use its own `telegram.prod.apiId` and `telegram.prod.apiHash`. Push
notifications through Firebase are optional: set `firebase.apiKey`, `firebase.projectId`,
`firebase.senderId` and `firebase.appId` (`firebase.prod.appId` for prod) in `local.properties`,
or put a `google-services.json` with clients for `app.miogram` and `app.miogram.prod` in `app/`.
Without either the app builds and runs without push. Release builds are signed with
`signing.storeFile`, `signing.storePassword`, `signing.keyAlias` and `signing.keyPassword` when
set, otherwise with the debug key.

## License

Apache License 2.0, see `LICENSE`. Third-party material is listed in `NOTICE`.
