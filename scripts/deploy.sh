#!/usr/bin/env bash
#
# Compile l'application et l'installe sur un téléphone Android branché en ADB.
#
#   ./scripts/deploy.sh                 # build debug + install + lancement
#   ./scripts/deploy.sh --release       # variante release
#   ./scripts/deploy.sh -s SERIAL       # cible un appareil précis
#   ./scripts/deploy.sh --help          # toutes les options
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE="com.demicourse.seance"
ACTIVITY=".MainActivity"

VARIANT="debug"
SERIAL="${ANDROID_SERIAL:-}"
CONNECT=""
DO_BUILD=1
DO_LAUNCH=1
DO_UNINSTALL=0
DO_LOGCAT=0

# --- sortie -----------------------------------------------------------------

if [[ -t 1 ]]; then
    C_RESET=$'\033[0m'; C_BOLD=$'\033[1m'; C_DIM=$'\033[2m'
    C_RED=$'\033[31m'; C_GREEN=$'\033[32m'; C_YELLOW=$'\033[33m'
else
    C_RESET=""; C_BOLD=""; C_DIM=""; C_RED=""; C_GREEN=""; C_YELLOW=""
fi

info()  { printf '%s==>%s %s\n' "$C_BOLD" "$C_RESET" "$*"; }
warn()  { printf '%s/!\\%s %s\n' "$C_YELLOW" "$C_RESET" "$*" >&2; }
die()   { printf '%serreur:%s %s\n' "$C_RED" "$C_RESET" "$*" >&2; exit 1; }
ok()    { printf '%s✓%s %s\n' "$C_GREEN" "$C_RESET" "$*"; }

usage() {
    cat <<'EOF'
Usage: scripts/deploy.sh [options]

Compile l'APK avec Gradle puis l'installe sur un téléphone Android via ADB.

Options :
  -r, --release          Compile la variante release au lieu de debug.
  -s, --serial SERIAL    Numéro de série de l'appareil (sinon : détection auto,
                         ou variable d'environnement ANDROID_SERIAL).
  -c, --connect HOTE[:PORT]
                         Se connecte d'abord en ADB sans fil (port 5555 par
                         défaut) et utilise cet appareil comme cible.
  -u, --uninstall        Désinstalle l'application avant d'installer (utile en
                         cas de signature ou de schéma de données incompatible).
      --no-build         N'appelle pas Gradle, installe l'APK déjà compilé.
      --no-launch        Installe sans lancer l'application.
  -l, --logcat           Suit les logs de l'application après le lancement.
  -h, --help             Affiche cette aide.

Variables d'environnement :
  ANDROID_HOME / ANDROID_SDK_ROOT   Emplacement du SDK Android (pour trouver adb).
  ANDROID_SERIAL                    Appareil cible par défaut.

Prérequis côté téléphone : options développeur activées et « débogage USB »
autorisé pour cet ordinateur.
EOF
}

# --- options ----------------------------------------------------------------

while [[ $# -gt 0 ]]; do
    case "$1" in
        -r|--release)   VARIANT="release"; shift ;;
        -s|--serial)    [[ $# -ge 2 ]] || die "--serial attend un numéro de série"; SERIAL="$2"; shift 2 ;;
        -c|--connect)   [[ $# -ge 2 ]] || die "--connect attend une adresse"; CONNECT="$2"; shift 2 ;;
        -u|--uninstall) DO_UNINSTALL=1; shift ;;
        --no-build)     DO_BUILD=0; shift ;;
        --no-launch)    DO_LAUNCH=0; shift ;;
        -l|--logcat)    DO_LOGCAT=1; shift ;;
        -h|--help)      usage; exit 0 ;;
        *)              usage >&2; die "option inconnue : $1" ;;
    esac
done

# --- adb --------------------------------------------------------------------

find_adb() {
    if command -v adb >/dev/null 2>&1; then
        command -v adb
        return
    fi
    local sdk
    for sdk in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" "$HOME/android-sdk"; do
        [[ -n "$sdk" && -x "$sdk/platform-tools/adb" ]] && { printf '%s\n' "$sdk/platform-tools/adb"; return; }
    done
    return 1
}

ADB="$(find_adb)" || die "adb introuvable. Installez les platform-tools Android \
(par ex. « sudo apt install adb ») ou définissez ANDROID_HOME."

adb_cmd() { "$ADB" ${SERIAL:+-s "$SERIAL"} "$@"; }

# Appareils dont l'état est « device » (ni offline, ni unauthorized).
list_devices() {
    "$ADB" devices | awk 'NR > 1 && $2 == "device" { print $1 }'
}

# Appareils visibles mais inutilisables, pour un message d'erreur utile.
list_unusable_devices() {
    "$ADB" devices | awk 'NR > 1 && NF >= 2 && $2 != "device" { print $1 " (" $2 ")" }'
}

if [[ -n "$CONNECT" ]]; then
    [[ "$CONNECT" == *:* ]] || CONNECT="$CONNECT:5555"
    info "Connexion sans fil à $CONNECT"
    "$ADB" connect "$CONNECT" >/dev/null || die "échec de « adb connect $CONNECT »"
    SERIAL="$CONNECT"
fi

info "Démarrage du serveur ADB"
"$ADB" start-server >/dev/null

if [[ -z "$SERIAL" ]]; then
    mapfile -t devices < <(list_devices)
    case "${#devices[@]}" in
        0)
            unusable="$(list_unusable_devices)"
            if [[ -n "$unusable" ]]; then
                warn "Appareils détectés mais indisponibles :"
                printf '%s\n' "$unusable" | sed 's/^/    /' >&2
                die "déverrouillez le téléphone et autorisez le débogage USB pour cet ordinateur."
            fi
            die "aucun appareil ADB détecté. Branchez le téléphone, activez le \
débogage USB, puis relancez (« adb devices » pour vérifier)."
            ;;
        1) SERIAL="${devices[0]}" ;;
        *)
            warn "Plusieurs appareils connectés :"
            printf '    %s\n' "${devices[@]}" >&2
            die "précisez la cible avec « --serial <numéro> »."
            ;;
    esac
fi

model="$(adb_cmd shell getprop ro.product.model 2>/dev/null | tr -d '\r' || true)"
android="$(adb_cmd shell getprop ro.build.version.release 2>/dev/null | tr -d '\r' || true)"
ok "Appareil : $SERIAL${model:+ — $model}${android:+ (Android $android)}"

# --- SDK Android ------------------------------------------------------------

# Gradle a besoin de l'emplacement du SDK ; l'avoir uniquement dans le PATH via
# adb ne suffit pas. On le résout ici pour donner une erreur lisible plutôt que
# le « SDK location not found » de l'Android Gradle Plugin.
is_sdk_dir() {
    local dir="$1" sub
    [[ -d "$dir" ]] || return 1
    for sub in platform-tools platforms build-tools cmdline-tools tools; do
        [[ -d "$dir/$sub" ]] && return 0
    done
    return 1
}

find_sdk() {
    local candidate adb_dir

    for candidate in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
        [[ -n "$candidate" ]] && is_sdk_dir "$candidate" && { printf '%s\n' "$candidate"; return; }
    done

    # sdk.dir de local.properties (ce qu'écrit Android Studio).
    if [[ -f "$ROOT_DIR/local.properties" ]]; then
        candidate="$(sed -n 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*//p' "$ROOT_DIR/local.properties" | tail -1)"
        candidate="${candidate//\\:/:}"
        [[ -n "$candidate" ]] && is_sdk_dir "$candidate" && { printf '%s\n' "$candidate"; return; }
    fi

    # Déduit du chemin d'adb : <sdk>/platform-tools/adb.
    adb_dir="$(dirname "$(readlink -f "$ADB")")"
    if [[ "$(basename "$adb_dir")" == "platform-tools" ]] && is_sdk_dir "$(dirname "$adb_dir")"; then
        printf '%s\n' "$(dirname "$adb_dir")"
        return
    fi

    for candidate in "$HOME/Android/Sdk" "$HOME/android-sdk" "/opt/android-sdk" "/usr/lib/android-sdk"; do
        is_sdk_dir "$candidate" && { printf '%s\n' "$candidate"; return; }
    done

    return 1
}

if [[ $DO_BUILD -eq 1 ]]; then
    if SDK="$(find_sdk)"; then
        ok "SDK Android : $SDK"
        export ANDROID_HOME="$SDK"
        export ANDROID_SDK_ROOT="$SDK"
    else
        warn "SDK Android introuvable — Gradle ne pourra pas compiler le module app."
        cat >&2 <<EOF
    Trois façons de le déclarer :
      • définir ANDROID_HOME (par ex. « export ANDROID_HOME=\$HOME/Android/Sdk ») ;
      • écrire « sdk.dir=/chemin/vers/le/sdk » dans $ROOT_DIR/local.properties ;
      • installer le SDK depuis Android Studio, ou les cmdline-tools puis
        « sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" ».
    Le paquet « adb » seul ne fournit pas de SDK complet.
EOF
        die "emplacement du SDK Android inconnu."
    fi
fi

# --- build ------------------------------------------------------------------

if [[ "$VARIANT" == "release" ]]; then
    GRADLE_TASK=":app:assembleRelease"
    APK_DIR="$ROOT_DIR/app/build/outputs/apk/release"
else
    GRADLE_TASK=":app:assembleDebug"
    APK_DIR="$ROOT_DIR/app/build/outputs/apk/debug"
fi

if [[ $DO_BUILD -eq 1 ]]; then
    info "Compilation ($VARIANT) : ./gradlew $GRADLE_TASK"
    (cd "$ROOT_DIR" && ./gradlew "$GRADLE_TASK")
fi

find_apk() {
    local candidate
    for candidate in "$APK_DIR"/app-"$VARIANT".apk "$APK_DIR"/app-"$VARIANT"-unsigned.apk; do
        [[ -f "$candidate" ]] && { printf '%s\n' "$candidate"; return; }
    done
    candidate="$(find "$APK_DIR" -maxdepth 1 -name '*.apk' -print -quit 2>/dev/null || true)"
    [[ -n "$candidate" ]] && { printf '%s\n' "$candidate"; return; }
    return 1
}

APK="$(find_apk)" || die "aucun APK dans $APK_DIR. Relancez sans « --no-build »."

if [[ "$APK" == *-unsigned.apk ]]; then
    die "l'APK release n'est pas signé ($APK) et ne peut pas être installé. \
Configurez une signingConfig dans app/build.gradle.kts, ou utilisez la variante debug."
fi

ok "APK : ${APK#"$ROOT_DIR"/} ($(du -h "$APK" | cut -f1))"

# --- installation -----------------------------------------------------------

if [[ $DO_UNINSTALL -eq 1 ]]; then
    info "Désinstallation de $PACKAGE"
    adb_cmd uninstall "$PACKAGE" >/dev/null 2>&1 || warn "application non installée, rien à désinstaller."
fi

info "Installation sur $SERIAL"
install_log="$(mktemp)"
trap 'rm -f "$install_log"' EXIT

if ! adb_cmd install -r "$APK" >"$install_log" 2>&1; then
    cat "$install_log" >&2
    if grep -q 'INSTALL_FAILED_UPDATE_INCOMPATIBLE\|INSTALL_FAILED_VERSION_DOWNGRADE\|signatures do not match' "$install_log"; then
        die "une version incompatible est déjà installée. Relancez avec « --uninstall »."
    fi
    if grep -q 'INSTALL_FAILED_INSUFFICIENT_STORAGE' "$install_log"; then
        die "espace de stockage insuffisant sur le téléphone."
    fi
    die "échec de l'installation (voir le détail ci-dessus)."
fi
ok "Installée."

# --- lancement --------------------------------------------------------------

if [[ $DO_LAUNCH -eq 1 ]]; then
    info "Lancement de $PACKAGE$ACTIVITY"
    if ! adb_cmd shell am start -n "$PACKAGE/$PACKAGE$ACTIVITY" >/dev/null 2>&1; then
        warn "impossible de lancer l'application automatiquement ; ouvrez-la depuis le téléphone."
    fi
fi

if [[ $DO_LOGCAT -eq 1 ]]; then
    pid="$(adb_cmd shell pidof -s "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
    if [[ -n "$pid" ]]; then
        info "Logs (Ctrl-C pour quitter)"
        adb_cmd logcat --pid="$pid"
    else
        warn "processus $PACKAGE introuvable ; « adb logcat » manuellement si besoin."
    fi
fi

printf '%sTerminé.%s\n' "$C_DIM" "$C_RESET"
