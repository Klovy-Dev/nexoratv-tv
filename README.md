# NexoraTV — Android natif (TV + box + téléphone)

App **Kotlin / Jetpack Compose** pour Fire TV / Android TV / box Android /
téléphone / tablette. La version **Flutter** (`../site iptv`, repo
`Klovy-Dev/nexoratv-app`) reste pour **Windows et iOS**.

Voir [`PLAN.md`](PLAN.md) pour l'architecture et les jalons.

## Build

La machine de dev (4 Go RAM) ne tient pas un build complet en local →
**la compilation se fait en CI** (GitHub Actions, `.github/workflows/build.yml`).

- Push sur `main` → build debug, APK en artefact.
- Tag `tv-vX.Y.Z` → build release + GitHub Release.

En local (machine costaude) :

```bash
./gradlew :app:assembleDebug
```

## Stack

Kotlin · Compose + Compose for TV · **Media3 / ExoPlayer + décodeurs FFmpeg
(nextlib)** · Room · OkHttp · Coil · minSdk 22.
