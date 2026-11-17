# demi-course

App Android (Kotlin + Jetpack Compose) pour planifier **une** séance de course (étapes : allure cible, distance ou durée, répétitions, récupération) et calculer où faire demi-tour. Pas d'historique : une séance à la fois, stockée localement. Portée depuis un prototype Claude Design.

Lire ce fichier suffit pour comprendre le projet ; n'ouvrir `chats/` et `project/` que pour trancher un comportement qui semble arbitraire :
- `chats/chat1.md`, `chats/chat2.md` : conversation de conception, **source de vérité du « pourquoi »** (récupération après chaque répétition, Tab/Entrée, règles `mm.ss`).
- `project/Séance.dc.html` : prototype HTML/JS (textes, espacements, formules de référence).

## Stack
- Kotlin 2.0.21, AGP 8.7.3, Gradle 8.14.3 (wrapper, somme SHA-256 vérifiée), Compose BOM 2024.12.01, `minSdk 26`, `compileSdk`/`targetSdk 35`.
- JDK 17 (Temurin) — voir « Décisions techniques ». DataStore Preferences + kotlinx.serialization. Versions : `gradle/libs.versions.toml`.
- Packages : `com.demicourse.domain`, `com.demicourse.seance`. Licence GPL-3.0.

## Environnement
- JDK épinglé dans `.sdkmanrc` (lu aussi par la CI via `java-version-file`) ; `sdk env install && sdk env` (auto avec `sdkman_auto_env=true`, détail dans le README).
- Toute montée de version d'outil : `.sdkmanrc`, `compileOptions`/`jvmTarget` des deux modules et README dans le même commit.
- **Limite du bac à sable cloud** : `dl.google.com` est bloqué, donc AGP et AndroidX ne se résolvent pas ; `:app:*` ne compile **pas** ici. `:domain:*` fonctionne (Maven Central peut répondre 429 : réessayer). La compilation de `app/` est vérifiée par la CI → toute modification de `app/` n'est « verte » qu'après le run CI de la PR.

## Commandes
- Lint : `./gradlew ktlintCheck` ; correction : `./gradlew ktlintFormat` (ktlint 1.8.0, règles dans `.editorconfig` ; `app` ne se formate pas ici, voir plus bas).
- Tests : `./gradlew :domain:test` (une classe : `--tests "*PaceMathTest"`).
- Build : `./gradlew :app:assembleDebug` (CI uniquement dans le cloud).
- Installer sur téléphone (ADB) : `./scripts/deploy.sh` (`--help`).

## CI (`.github/workflows/`)
- `ci.yml` sur chaque PR et push `main` : `lint` (ktlint), `test` (domain), `build` (APK debug), `security` (gitleaks) → job `report` (`if: always()`) qui commente la PR. Toute nouvelle vérification s'ajoute aussi aux `needs` de `report`.
- `release.yml` : **manuel** (`workflow_dispatch`, `main` uniquement) ; tag `yyyy.MM.dd-N` + APK signé + `.sha256`. Merger sur `main` ne publie rien.
- Actions épinglées par SHA (commentaire `# vX.Y.Z`), maintenues par Dependabot (`.github/dependabot.yml`, gradle + actions). Permissions minimales par job, `persist-credentials: false`.
- Protéger `main` : jobs `lint`, `test`, `build`, `security` en checks obligatoires.

## Équipe d'agents (`.claude/agents/`)
| Agent | Rôle | Modèle | Écrit |
|---|---|---|---|
| Chef Bouillon (session principale) | seul développeur, expert Kotlin/Compose/Gradle/CI | session | oui |
| `gardien-du-phare` | UX & accessibilité Android | sonnet | non |
| `turbo-escargot` | performances | sonnet | non |
| `cerbere-le-paranoiaque` | sécurité & CVE | opus | non |
| `maniaque-du-plumeau` | clean code & architecture | sonnet | non |
| `capitaine-assertion` | stratégie de tests | sonnet | non |
| `madame-irma-des-versions` | versions & compatibilités | haiku | non |

Les spécialistes ne font que des rapports (sévérité, `fichier:ligne`, recommandation) ; ne solliciter que ceux concernés par le changement ; jamais de suppression d'agent sans accord. Leur `model:` est explicite (jamais `inherit`) ; le changer seulement sur preuve et le consigner ici. Pas de `rest-en-paix` (pas d'API), pas de Lighthouse (pas de web : Android Lint/Accessibility Scanner à la place).

## Méthode de travail
- Tâche non triviale : plan d'abord, « go » d'Adrien avant de coder ; écart significatif → resoumettre. Recommandation d'un spécialiste contestable → le chef tranche et justifie ; enjeu significatif → Adrien.
- **Done** = CI verte sur le dernier commit poussé (suivre le run, lire les logs, corriger la cause racine). Interdit pour obtenir le vert : skipper un test, assouplir ktlint, `continue-on-error`, supprimer un job, baisser un seuil. Échec non lié : un seul relancement, puis diagnostic. CI inaccessible : « terminé localement, CI non vérifiée ».
- Après un changement de `app/` : le formatage ktlint se fait via une copie de travail où `domain` pointe sur `app/src/main/kotlin` (`srcDir`), car `:app:ktlintFormat` ne se configure pas hors AGP ; ou laisser la CI signaler.

## Architecture
Deux modules pour tester le calcul sans la toolchain Android ; chaque module applique ses propres plugins (pas de bloc `apply false` à la racine, ce qui permet à `:domain:test` de tourner sans AGP).

**`domain/`** (Kotlin pur, testé dans `domain/src/test`) :
- `Models.kt` — `StepSpec` sert **à la fois** d'étape et de modèle (modèle = + `id`/`name`) : ne pas créer de type `Template`. `MAX_REPS = 99` borne les répétitions (saisie, validation et calcul).
- `PaceMath.kt` — parsing/formatage `mm.ss` et distance, `piece()`/`metrics()` (allure simple ou plage × distance ou durée → enveloppe distance/temps). Allure = secondes par unité de distance ; étape en distance : `dMin == dMax` ; en durée : `tMin == tMax`.
- `SessionCalculator.kt` — aplatit les étapes en segments effort/récupération, somme, puis cherche le demi-tour à la moitié de la distance **ou** de la durée (`HalfBy`). Un segment compte pour le **milieu** de son enveloppe : une plage d'allure ne l'élargit pas.

**`app/`** (Compose) :
- `data/SeanceRepository.kt` — seule persistance : un DataStore, trois blobs JSON (étapes, modèles, réglages). Lu **une fois** au démarrage par le ViewModel, qui est le seul écrivain (état mémoire = source de vérité).
- `ui/SeanceViewModel.kt` + `ui/UiState.kt` — un seul `SheetState` pour une feuille réutilisée trois fois (`STEP`, `TEMPLATE`, `SETTINGS`). `fieldOrder(sheet)` liste les champs pertinents (change avec plage/récupération/« enregistrer comme modèle ») : **seule source de vérité** de la chaîne de focus. `Draft.toStepSpec()` est l'unique conversion brouillon → étape.
- `ui/components/SheetFocusController.kt` + `SeanceTextField.kt` — clavier du prototype : Entrée (ou « suivant ») avance **et sélectionne** le texte, soumet au dernier champ ; Tab avance **sans** sélectionner (asymétrie voulue, voir `chats/chat1.md`). La sélection n'a lieu qu'à l'ouverture de la feuille ou à une avance explicite, jamais au simple clic dans un champ (bug du prototype corrigé : ne pas câbler select-all sur le gain de focus).
- `ui/theme/Theme.kt` — `SeanceColors` (CompositionLocal) reflète 1:1 les variables CSS du prototype ; utiliser `LocalSeanceColors.current`, pas `MaterialTheme.colorScheme`.
- `ui/Formatting.kt` — textes d'affichage (puces, totaux, marqueur de demi-tour), port de `renderVals()` ; à garder cohérent avec `PaceMath`/`SessionCalculator`.
- Accessibilité : cibles tactiles ≥ 48 dp via `MIN_TOUCH_TARGET` (le dessin reste petit), `clickable(onClickLabel, role)`, glyphes décoratifs en `clearAndSetSemantics {}`, libellés de champ dérivés de `FieldKey` dans `SeanceTextField`.

## Règles fonctionnelles
- `mm.ss` en **saisie** : `1.30` = 1 min 30 s ; un seul chiffre après le séparateur = unités (`2.1` = `2.01`) ; secondes ≥ 60 rejetées. En **affichage** : `3m02s`, `3m02s/km` (`formatDuration`, `formatMmSsField`), sans partie nulle — divergence voulue avec le prototype.
- Le marqueur de demi-tour indique aussi le reste du **segment** (une répétition ou une récupération, jamais les répétitions suivantes : `TurnPoint.restDistance`/`restTime`). Unité : une récupération est toujours en temps ; un effort suit la `measure` de son étape → valeur exacte, pas dérivée de l'allure.
- Récupération (si activée) après **chaque** répétition, la dernière comprise. Défaut : plage 9.00–11.00 (`AppSettings.recovery`, éditable dans les réglages, préremplit les nouvelles étapes).
- Un modèle exige au moins l'allure **ou** la longueur. `applyTemplate` copie reps/nom toujours, allure/longueur/récupération seulement si définies.
- Répétitions : 1 à 99 (message « Répétitions : de 1 à 99 »).
- Thème `SYSTEM`/`LIGHT`/`DARK` dans `AppSettings.theme`. `AppSettings.unit` et `halfBy` n'ont pas d'UI aujourd'hui (valeurs par défaut km et distance).

## Variantes et versions (`app/build.gradle.kts`, rien en dur)
- **debug = dev** : `applicationIdSuffix ".dev"`, nom « Demi Course (dev) » (`resValue app_name`, **pas** dans `strings.xml`), version `dev-yyyy.MM.dd-HH.mm`, code = minutes depuis 2020.
- **release = prod** : version `yyyy.MM.dd-N` (date de publication + rang du jour calculé par `release.yml` → `-PappVersionName/-PappVersionCode`), code `yyMMddNN` (N ≤ 99). Sans propriété : rang 0 = jamais publié.
- Version appliquée par variante via `androidComponents.onVariants` ; `BuildConfig.APP_VERSION_NAME` (par type de build) s'affiche en bas des réglages.
- Signature : `keystore.properties` ou variables `ANDROID_KEYSTORE_*` (secrets du dépôt) ; sans clé, APK non signé donc non installable (signalé dans les notes). `scripts/setup-signing.sh` crée la clé et pose les secrets ; idempotent, ne change jamais de clé (un changement rend les installations existantes non mettables à jour). Sauvegardes de clé ignorées par git.

## Décisions techniques
- 2026-09 — `StepSpec` unique pour étape et modèle ; prototype Claude Design comme référence.
- 2026-09 — Release uniquement manuelle (merge ≠ publication).
- 2026-11 — Agents spécialistes et `ci.yml` introduits (skill `regles-projet`) ; ktlint adopté (formatage officiel, `Composable` exempté de la règle de nommage).
- 2026-11 — **JDK 17 conservé** : AGP 8.7.3 / Kotlin 2.0.21 / Gradle 8.14 ne supportent pas Java 25 ; la LTS courante suppose une montée groupée AGP 9 + Kotlin 2.x + Gradle 9 + compileSdk 37 (non vérifiable hors CI), soumise à arbitrage d'Adrien.
- 2026-11 — Pas de `Dockerfile` : app Android sans service, build natif sur runner GitHub (écart au skill, à confirmer par Adrien).
- 2026-11 — Commits en **Conventional Commits anglais** (règle du skill), à la place du français d'avant ; l'historique antérieur reste en français.

## Pièges
- `app/*` ne compile pas dans le cloud : relire les imports et signatures à la main, puis attendre la CI. Un `StepSpec` non stable côté Compose : ne pas le recréer inutilement.
- Lire le flux DataStore en continu écraserait l'état optimiste (scintillement, étapes ressuscitées) : garder la lecture unique.
- `Formatting` et le ViewModel n'ont pas de tests (code `app/`) ; toute logique pure doit vivre dans `domain/` pour être testée ici.
- Maven Central renvoie parfois 429 depuis le cloud : relancer la commande.

## État du projet
- Fait : édition d'étapes/modèles, calcul du demi-tour (distance ou durée), thèmes, persistance locale, variantes dev/prod, release manuelle signée, CI complète, audit sécurité/qualité/a11y (2026-11).
- Dette connue / à arbitrer : extraire `Draft`/`validate`/`fieldOrder`/`Formatting` vers `domain` pour les tester ; annulation après suppression d'étape ; UI pour `halfBy`/`unit` ; R8 (`isMinifyEnabled`) ; montée de toolchain (voir ci-dessus) ; demi-tour pile à une frontière de segment (reste 0).

## Git
- Conventional Commits en anglais, impératif ; branche `claude/<sujet-en-kebab-case>` parlante (jamais un nom généré : renommer avant le premier push).
- Une session ouvre au plus une PR, sauf demande contraire : un second sujet va sur la même branche et élargit le titre.
- `CHANGELOG.md` (Keep a Changelog) mis à jour pour tout changement notable ; ce fichier mis à jour dans le même commit que ce qu'il décrit.

## Règles de code
- Clean code (nommage explicite, fonctions courtes, pas de nombre magique, pas de code mort, commentaires pour le « pourquoi »), Kotlin idiomatique, bonnes pratiques officielles. Tout nouveau code accompagné de tests (`domain`).
- Textes d'interface en français.
