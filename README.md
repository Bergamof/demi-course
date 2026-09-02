# demi-course

Une application Android permettant de configurer une séance de course à pieds pour savoir à quel moment faire demi-tour : allure cible (unique ou intervalle), distance ou durée, répétitions, récupération, modèles réutilisables, et calcul automatique du point de demi-tour à mi-parcours.

## Structure

- `domain/` — module Kotlin pur : parsing des formats `mm.ss`, calcul des métriques par étape et du point de demi-tour (testé unitairement).
- `app/` — application Android (Kotlin + Jetpack Compose), thèmes clair/sombre/système, persistance locale (DataStore) de la séance, des modèles et des réglages.
- `chats/`, `project/` — bundle de conception original (Claude Design) ayant servi de base à l'implémentation ; conservé pour référence.

## Build

```
./gradlew :domain:test        # tests du module de calcul
./gradlew :app:assembleDebug  # build de l'application
```
