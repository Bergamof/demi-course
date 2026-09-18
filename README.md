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

### 1. Créer la clé de signature

Une seule fois, avec le `keytool` du JDK :

```
keytool -genkeypair -v \
        -keystore release.jks -storetype PKCS12 \
        -alias demi-course \
        -keyalg RSA -keysize 4096 -validity 10000 \
        -dname "CN=Demi Course"
```

`keytool` demande un mot de passe. En PKCS12, le keystore et la clé partagent le
même : `ANDROID_KEYSTORE_PASSWORD` et `ANDROID_KEY_PASSWORD` auront donc la même
valeur. Ne passez pas le mot de passe en option (`-storepass`), il finirait dans
l'historique du shell.

**Sauvegardez `release.jks` et son mot de passe ailleurs que dans le dépôt** (il
est ignoré par git, et c'est voulu). Cette clé est l'identité de l'application
pour Android : la perdre signifie que les versions suivantes ne pourront plus
être installées par-dessus celles déjà sur le téléphone — il faudra désinstaller
puis réinstaller, en perdant les données.

### 2. Créer les secrets du dépôt

Quatre secrets, dans Settings → Secrets and variables → Actions → New repository
secret :

| Secret                      | Valeur                                        |
|-----------------------------|-----------------------------------------------|
| `ANDROID_KEYSTORE_BASE64`   | la sortie de `base64 -w0 release.jks`         |
| `ANDROID_KEYSTORE_PASSWORD` | le mot de passe choisi à l'étape 1            |
| `ANDROID_KEY_ALIAS`         | `demi-course` (l'`-alias` de l'étape 1)       |
| `ANDROID_KEY_PASSWORD`      | le même mot de passe                          |

Ou en ligne de commande, avec le [CLI GitHub](https://cli.github.com) :

```
base64 -w0 release.jks | gh secret set ANDROID_KEYSTORE_BASE64
gh secret set ANDROID_KEYSTORE_PASSWORD      # demande la valeur, sans l'afficher
gh secret set ANDROID_KEY_ALIAS --body demi-course
gh secret set ANDROID_KEY_PASSWORD
```

Le prochain merge sur `main` publiera un APK signé. Gardez la même clé d'une
release à l'autre : une signature qui change et c'est la mise à jour que le
téléphone rejette.

### 3. Signer un build de prod en local (facultatif)

Pour `./scripts/deploy.sh --release`, mettez les mêmes valeurs dans un
`keystore.properties` à la racine (ignoré par git) :

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
