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
Sans les secrets ci-dessous, la release est tout de même publiée mais l'APK n'est
pas signé et Android refusera de l'installer ; les notes de release le signalent.

### 1. Créer la clé et poser les secrets

```
./scripts/setup-signing.sh
```

Le script crée `release.jks` (RSA 4096, PKCS12, mot de passe tiré au hasard),
enregistre le mot de passe dans `keystore.properties` — les deux en lecture
pour vous seul, et ignorés par git — puis pose les quatre secrets sur le dépôt
via le CLI GitHub. Relancé alors que la clé existe, il la réutilise au lieu
d'en fabriquer une autre.

Prérequis : un JDK (pour `keytool`) et [`gh`](https://cli.github.com)
authentifié (`gh auth login`) avec le droit d'écrire les secrets du dépôt.

Options utiles :

```
./scripts/setup-signing.sh --secrets-only   # republie les secrets d'une clé existante
./scripts/setup-signing.sh --no-secrets     # crée seulement la clé, sans toucher à GitHub
./scripts/setup-signing.sh --ask-password   # choisir le mot de passe au lieu d'en tirer un
./scripts/setup-signing.sh --help           # toutes les options
```

**Sauvegardez `release.jks` et `keystore.properties` hors du dépôt.** Cette clé
est l'identité de l'application pour Android : la perdre signifie que les
versions suivantes ne pourront plus être installées par-dessus celles déjà sur
le téléphone — il faudra désinstaller puis réinstaller, en perdant les données.
Pour la même raison, ne changez pas de clé d'une release à l'autre (le script
refuse d'en remplacer une sans `--force` et une confirmation explicite).

### 2. Ou à la main

Sans `gh`, ou pour comprendre ce que fait le script :

```
keytool -genkeypair -v \
        -keystore release.jks -storetype PKCS12 \
        -alias demi-course \
        -keyalg RSA -keysize 4096 -validity 10000 \
        -dname "CN=Demi Course"
```

`keytool` demande un mot de passe. En PKCS12, le keystore et la clé partagent
le même : `ANDROID_KEYSTORE_PASSWORD` et `ANDROID_KEY_PASSWORD` auront donc la
même valeur. Ne passez pas le mot de passe en option (`-storepass`), il
finirait dans l'historique du shell.

Puis quatre secrets, dans Settings → Secrets and variables → Actions → New
repository secret :

| Secret                      | Valeur                                        |
|-----------------------------|-----------------------------------------------|
| `ANDROID_KEYSTORE_BASE64`   | la sortie de `base64 -w0 release.jks`         |
| `ANDROID_KEYSTORE_PASSWORD` | le mot de passe choisi ci-dessus              |
| `ANDROID_KEY_ALIAS`         | `demi-course` (l'`-alias` ci-dessus)          |
| `ANDROID_KEY_PASSWORD`      | le même mot de passe                          |

### 3. Signer un build de prod en local (facultatif)

`./scripts/deploy.sh --release` lit le `keystore.properties` que le script a
écrit ; rien de plus à faire. En partant d'une clé existante, écrivez-le
vous-même à la racine :

```
storeFile=/chemin/vers/release.jks
storePassword=…
keyAlias=demi-course
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
