# demi-course

Une application Android permettant de configurer une séance de course à pieds pour savoir à quel moment faire demi-tour : allure cible (unique ou intervalle), distance ou durée, répétitions, récupération, modèles réutilisables, et calcul automatique du point de demi-tour à mi-parcours.

## Structure

- `domain/` — module Kotlin pur : parsing des formats `mm.ss`, calcul des métriques par étape et du point de demi-tour (testé unitairement).
- `app/` — application Android (Kotlin + Jetpack Compose), thèmes clair/sombre/système, persistance locale (DataStore) de la séance, des modèles et des réglages.
- `chats/`, `project/` — bundle de conception original (Claude Design) ayant servi de base à l'implémentation ; conservé pour référence.

## Build

Le projet cible Java 17. La version du JDK est figée dans `.sdkmanrc` pour
[SDKMAN](https://sdkman.io) :

```
sdk env install   # installe le JDK du projet s'il manque
sdk env           # bascule le shell courant dessus
```

```
./gradlew :domain:test        # tests du module de calcul
./gradlew :app:assembleDebug  # build de l'application
```

## Installation sur un téléphone (Linux)

`scripts/deploy.sh` compile l'APK et l'installe sur un téléphone branché en USB
via ADB, puis lance l'application.

```
./scripts/deploy.sh                    # build debug + installation + lancement
./scripts/deploy.sh --release          # variante release (APK signé requis)
./scripts/deploy.sh -s SERIAL          # cible un appareil précis
./scripts/deploy.sh -c 192.168.1.42    # ADB sans fil
./scripts/deploy.sh --no-build -l      # réinstalle l'APK existant et suit les logs
./scripts/deploy.sh --help             # toutes les options
```

Prérequis : les *platform-tools* Android (`adb` dans le `PATH`, ou `ANDROID_HOME`
défini), un JDK 17, et sur le téléphone les options développeur avec le
« débogage USB » autorisé pour cet ordinateur.
