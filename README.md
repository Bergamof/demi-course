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

## Versions

Deux variantes, deux applications distinctes sur le téléphone :

| Variante | Nom affiché         | applicationId              | Version                |
|----------|---------------------|----------------------------|------------------------|
| dev      | Demi Course (dev)   | `com.demicourse.seance.dev`| `dev-2026.09.18-12.23` |
| prod     | Demi Course         | `com.demicourse.seance`    | `2026.09.18-1`         |

- **dev** : date et heure du build. L'`applicationId` étant suffixé `.dev`,
  installer une version de développement n'écrase jamais la version de prod —
  les deux cohabitent, avec chacune ses données.
- **prod** : date de publication et rang de la publication dans la journée. La
  première release du 18 septembre 2026 est `2026.09.18-1`, la deuxième du même
  jour `2026.09.18-2`. Le rang est calculé à partir des tags déjà publiés.

La version est rappelée discrètement tout en bas de l'écran des paramètres de
l'application.

## Publication

Tout push sur `main` (typiquement un merge de PR) déclenche
`.github/workflows/release.yml` : calcul de la version du jour, tests du module
`domain`, build de l'APK de prod, puis création d'une release GitHub taguée avec
cette version et portant l'APK en pièce jointe.

Pour que l'APK publié soit installable, il doit être signé avec une clé stable.
Renseignez ces secrets dans le dépôt (Settings → Secrets and variables →
Actions) :

| Secret                      | Contenu                                        |
|-----------------------------|------------------------------------------------|
| `ANDROID_KEYSTORE_BASE64`   | le keystore encodé (`base64 -w0 release.jks`)  |
| `ANDROID_KEYSTORE_PASSWORD` | mot de passe du keystore                       |
| `ANDROID_KEY_ALIAS`         | alias de la clé                                |
| `ANDROID_KEY_PASSWORD`      | mot de passe de la clé                         |

Sans ces secrets, la release est tout de même publiée mais l'APK n'est pas signé
et Android refusera de l'installer ; les notes de release le signalent. Gardez la
même clé d'une release à l'autre, sinon les mises à jour seront rejetées par le
téléphone.

Pour un build de prod en local, mettez les mêmes valeurs dans un
`keystore.properties` à la racine (ignoré par git) :

```
storeFile=/chemin/vers/release.jks
storePassword=…
keyAlias=…
keyPassword=…
```

## Installation sur un téléphone (Linux)

`scripts/deploy.sh` compile l'APK et l'installe sur un téléphone branché en USB
via ADB, puis lance l'application. Par défaut, c'est la version de
développement.

```
./scripts/deploy.sh                    # build dev + installation + lancement
./scripts/deploy.sh --release          # version de prod (APK signé requis)
./scripts/deploy.sh -s SERIAL          # cible un appareil précis
./scripts/deploy.sh -c 192.168.1.42    # ADB sans fil
./scripts/deploy.sh --no-build -l      # réinstalle l'APK existant et suit les logs
./scripts/deploy.sh --help             # toutes les options
```

Si plusieurs appareils sont connectés, le script affiche un menu de sélection
(modèle et version d'Android de chacun). `-s` court-circuite le menu, ce qui est
nécessaire quand le script tourne sans terminal interactif.

Prérequis :

- `adb` (dans le `PATH`, ou trouvé via `ANDROID_HOME` / `ANDROID_SDK_ROOT`) ;
- un SDK Android complet pour la compilation — le paquet `adb` seul ne suffit
  pas. Le script le cherche via `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `sdk.dir`
  dans `local.properties`, le chemin d'`adb`, puis `~/Android/Sdk` ;
- un JDK 17 (voir `.sdkmanrc` ci-dessus) ;
- sur le téléphone : options développeur activées et « débogage USB » autorisé
  pour cet ordinateur.
