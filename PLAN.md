# NexoraTV — app native Android (TV + box + téléphone)

App **Kotlin native** pour Fire TV / Android TV / box Android / téléphone /
tablette. La version **Flutter** (`Documents/gaby/site iptv`) reste pour
**Windows et iOS** uniquement.

Objectif : la fluidité d'IBOGOLD (natif + ExoPlayer), en gardant les
fonctionnalités de NexoraTV.

## Stack

| Domaine | Choix |
|---|---|
| Langage / UI | Kotlin, **Jetpack Compose + Compose for TV** (`androidx.tv:tv-material`) — une seule UI TV + téléphone |
| Lecteur | **Media3 / ExoPlayer** + décodeurs **FFmpeg** (`nextlib`, AC3/EAC3/DTS) + tunneling TV + gros `LoadControl` |
| Données | **Room** (SQLite) : sources, favoris, historique, cache catalogue |
| Réseau | OkHttp + `org.json` (les panneaux Xtream renvoient un Content-Type faux) |
| DI | Hilt |
| Images | Coil 3 |
| Async | Coroutines / Flow |
| minSdk | 22 (Fire OS 5) · targetSdk 35 |

## Ce qui est porté du Flutter (logique métier)

- `core/model` — `PlaylistSource`, `Channel`, `Series`, `Episode`, `LoadedPlaylist`
- `core/xtream/XtreamClient` — `player_api.php`, auth, live/vod/séries, ordre
  des catégories = ordre du panel, parsing tolérant (ratings /100, epochs…)
- `core/m3u/M3uParser` — `#EXTINF` / `#EXTGRP` / attributs `tvg-*`
- À porter : détection `get.php` → Xtream, portail MAC, EPG XMLTV, TMDB,
  fusion des catégories de qualité, MAJ intégrées

## Jalons

- [x] **M0 — Squelette** : projet Gradle, Hilt, thème bleu nuit, `MainActivity`
      Compose, icônes + bannière TV, modèles, `XtreamClient`, `M3uParser`, DB Room.
- [ ] **M1 — Lecture** : `PlayerActivity` Media3 (ExoPlayer + FFmpeg), contrôles
      D-pad, pistes audio/sous-titres, reprise VOD, zap live, chien de garde
      reconnexion. ← *priorité, c'est le cœur du besoin*
- [ ] **M2 — Sources** : écran ajout (Xtream / M3U), validation, stockage Room,
      multi-sources + bascule.
- [ ] **M3 — Catalogue** : chargement + cache, écran d'accueil (bandeau + rails),
      TV / Films / Séries (grille + panneau catégories), navigation D-pad.
- [ ] **M4 — Détail & séries** : fiche film/série, saisons/épisodes, favoris,
      « Reprendre », recherche.
- [ ] **M5 — EPG** : guide XMLTV + « en cours » sur les chaînes.
- [ ] **M6 — Réglages** : lecteur (tampon, sous-titres), contrôle parental,
      à propos, MAJ intégrées (manifeste `update.json`).
- [ ] **M7 — Distribution** : signature, CI GitHub Actions, tag `tv-vX.Y.Z`,
      lien `nexoratv.fr/tv.apk`.

## Non couvert (volontairement)

- iOS / Windows → restent sur la version Flutter.
- Apple TV → app tvOS Swift séparée (déjà décidé).
