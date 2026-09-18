#!/usr/bin/env bash
#
# Crée la clé de signature de l'application et dépose les quatre secrets
# correspondants sur GitHub, pour que le workflow de release publie un APK
# signé (et donc installable).
#
#   ./scripts/setup-signing.sh                 # crée la clé + pose les secrets
#   ./scripts/setup-signing.sh --secrets-only  # repose les secrets d'une clé existante
#   ./scripts/setup-signing.sh --help          # toutes les options
#
# La clé est écrite dans release.jks et son mot de passe dans
# keystore.properties, tous deux ignorés par git. Ces deux fichiers sont
# l'identité de l'application pour Android : sauvegardez-les hors du dépôt.
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPS="$ROOT_DIR/keystore.properties"

KEYSTORE="$ROOT_DIR/release.jks"
ALIAS="demi-course"
REPO=""
ASK_PASSWORD=0
DO_KEY=1
DO_SECRETS=1
FORCE=0

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
Usage: scripts/setup-signing.sh [options]

Crée la clé de signature de l'application et dépose sur GitHub les secrets
qu'utilise .github/workflows/release.yml : ANDROID_KEYSTORE_BASE64,
ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS et ANDROID_KEY_PASSWORD.

Sans option : crée release.jks avec un mot de passe aléatoire, l'enregistre
dans keystore.properties, puis pose les quatre secrets. Relancé alors que la
clé existe déjà, le script la réutilise au lieu d'en fabriquer une autre.

Options :
  -k, --keystore CHEMIN  Emplacement du keystore (défaut : <racine>/release.jks).
  -a, --alias ALIAS      Alias de la clé (défaut : demi-course).
  -r, --repo OWNER/REPO  Dépôt GitHub cible (défaut : celui du dossier courant).
      --ask-password     Demande un mot de passe au lieu d'en tirer un au hasard.
      --secrets-only     Ne touche pas à la clé, repose seulement les secrets.
      --no-secrets       Crée seulement la clé, sans rien envoyer à GitHub.
  -f, --force            Remplace une clé existante et des secrets existants.
                         À n'utiliser qu'en connaissance de cause : changer de
                         clé rend les mises à jour ininstallables sur les
                         téléphones qui ont déjà l'application.
  -h, --help             Affiche cette aide.

Prérequis : un JDK (pour keytool) et le CLI GitHub « gh », authentifié
(« gh auth login ») avec le droit d'écrire les secrets du dépôt.
EOF
}

# --- options ----------------------------------------------------------------

while [[ $# -gt 0 ]]; do
    case "$1" in
        -k|--keystore)  [[ $# -ge 2 ]] || die "--keystore attend un chemin"; KEYSTORE="$2"; shift 2 ;;
        -a|--alias)     [[ $# -ge 2 ]] || die "--alias attend un nom"; ALIAS="$2"; shift 2 ;;
        -r|--repo)      [[ $# -ge 2 ]] || die "--repo attend OWNER/REPO"; REPO="$2"; shift 2 ;;
        --ask-password) ASK_PASSWORD=1; shift ;;
        --secrets-only) DO_KEY=0; shift ;;
        --no-secrets)   DO_SECRETS=0; shift ;;
        -f|--force)     FORCE=1; shift ;;
        -h|--help)      usage; exit 0 ;;
        *)              usage >&2; die "option inconnue : $1" ;;
    esac
done

[[ $DO_KEY -eq 1 || $DO_SECRETS -eq 1 ]] || die "--secrets-only et --no-secrets s'annulent."

# --- prérequis --------------------------------------------------------------

if [[ $DO_KEY -eq 1 ]]; then
    command -v keytool >/dev/null 2>&1 || die "keytool introuvable. Installez un JDK 17 \
(« sdk env install » avec SDKMAN, cf. README)."
fi

if [[ $DO_SECRETS -eq 1 ]]; then
    command -v gh >/dev/null 2>&1 || die "« gh » introuvable. Installez le CLI GitHub \
(https://cli.github.com), ou relancez avec « --no-secrets » et posez les secrets à la main."
    gh auth status >/dev/null 2>&1 || die "« gh » n'est pas authentifié. Lancez « gh auth login »."

    if [[ -z "$REPO" ]]; then
        REPO="$(cd "$ROOT_DIR" && gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || true)"
        [[ -n "$REPO" ]] || die "dépôt GitHub non déterminé. Précisez-le avec « --repo OWNER/REPO »."
    fi
fi

# --- keystore.properties ----------------------------------------------------

# Lit une propriété du fichier sans l'interpréter comme du shell.
read_prop() {
    [[ -f "$PROPS" ]] || return 0
    sed -n "s/^[[:space:]]*$1[[:space:]]*=[[:space:]]*//p" "$PROPS" | tail -1
}

write_props() {
    local store_file="$1" password="$2" alias="$3"
    # Créé avec des droits restreints avant d'écrire quoi que ce soit dedans.
    : > "$PROPS"
    chmod 600 "$PROPS"
    cat > "$PROPS" <<EOF
# Clé de signature de la version de prod. Ignoré par git : ce fichier et le
# keystore qu'il désigne sont à sauvegarder ailleurs (cf. « Publication » dans
# le README). Les mêmes valeurs sont dans les secrets du dépôt GitHub.
storeFile=$store_file
storePassword=$password
keyAlias=$alias
keyPassword=$password
EOF
    ok "Mot de passe enregistré dans ${PROPS#"$ROOT_DIR"/}"
}

# Vérifie qu'un mot de passe ouvre bien le keystore et que l'alias s'y trouve.
keystore_opens() {
    local file="$1" password="$2" alias="$3"
    KEYSTORE_PASSWORD="$password" keytool -list \
        -keystore "$file" -alias "$alias" -storepass:env KEYSTORE_PASSWORD >/dev/null 2>&1
}

generate_password() {
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -hex 24
    else
        # Repli sans openssl ; « || true » car head ferme le tuyau avant la fin.
        { LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | head -c 48; } 2>/dev/null || true
        echo
    fi
}

ask_password() {
    local first second
    # -r /dev/tty ne suffit pas : le fichier existe même sans terminal de
    # contrôle, et c'est son ouverture qui échoue.
    { : < /dev/tty; } 2>/dev/null \
        || die "sans terminal interactif, impossible de demander un mot de passe."
    while true; do
        read -rsp "Mot de passe de la clé : " first < /dev/tty; printf '\n' >&2
        [[ -n "$first" ]] || { warn "mot de passe vide."; continue; }
        read -rsp "Confirmez : " second < /dev/tty; printf '\n' >&2
        [[ "$first" == "$second" ]] || { warn "les deux saisies diffèrent."; continue; }
        printf '%s' "$first"
        return
    done
}

PASSWORD=""

if [[ $DO_KEY -eq 1 ]]; then

    # Trois cas : la clé est déjà connue (on la réutilise), le fichier existe
    # sans qu'on ait son mot de passe (on le demande), ou il n'y a rien (on crée).
    if [[ -f "$PROPS" ]] && [[ $FORCE -eq 0 ]]; then
        KEYSTORE="$(read_prop storeFile)"
        PASSWORD="$(read_prop storePassword)"
        ALIAS="$(read_prop keyAlias)"
        [[ -n "$KEYSTORE" && -n "$PASSWORD" && -n "$ALIAS" ]] \
            || die "${PROPS#"$ROOT_DIR"/} est incomplet. Corrigez-le, ou supprimez-le pour repartir de zéro."
        [[ -f "$KEYSTORE" ]] \
            || die "le keystore « $KEYSTORE » annoncé par ${PROPS#"$ROOT_DIR"/} est introuvable. \
Restaurez-le depuis votre sauvegarde."
        keystore_opens "$KEYSTORE" "$PASSWORD" "$ALIAS" \
            || die "le mot de passe de ${PROPS#"$ROOT_DIR"/} n'ouvre pas « $KEYSTORE » (alias « $ALIAS »)."
        ok "Clé existante réutilisée : $KEYSTORE (alias « $ALIAS »)"

    elif [[ -f "$KEYSTORE" ]] && [[ $FORCE -eq 0 ]]; then
        info "Le keystore « $KEYSTORE » existe déjà mais son mot de passe n'est pas enregistré."
        PASSWORD="$(ask_password)"
        keystore_opens "$KEYSTORE" "$PASSWORD" "$ALIAS" \
            || die "ce mot de passe n'ouvre pas « $KEYSTORE » (alias « $ALIAS »). \
Vérifiez l'alias avec « keytool -list -keystore \"$KEYSTORE\" »."
        ok "Clé existante validée : $KEYSTORE (alias « $ALIAS »)"
        write_props "$KEYSTORE" "$PASSWORD" "$ALIAS"

    else
        if [[ -f "$KEYSTORE" ]]; then
            warn "« --force » : la clé « $KEYSTORE » va être remplacée."
            warn "Les téléphones ayant déjà l'application refuseront les mises à jour signées \
avec la nouvelle clé (il faudra désinstaller puis réinstaller, en perdant les données)."
            if { : < /dev/tty; } 2>/dev/null; then
                read -rp "Taper « remplacer » pour confirmer : " confirm < /dev/tty
                [[ "$confirm" == "remplacer" ]] || die "annulé."
            else
                die "sans terminal interactif, le remplacement d'une clé existante est refusé."
            fi
            mv "$KEYSTORE" "$KEYSTORE.$(date +%Y%m%d%H%M%S).bak"
            ok "Ancienne clé conservée à côté, suffixée « .bak »."
        fi

        if [[ $ASK_PASSWORD -eq 1 ]]; then
            PASSWORD="$(ask_password)"
        else
            PASSWORD="$(generate_password)"
            [[ ${#PASSWORD} -ge 24 ]] || die "génération du mot de passe impossible. Relancez avec « --ask-password »."
            info "Mot de passe tiré au hasard — rien à saisir, rien à retenir : il est \
enregistré dans keystore.properties (« --ask-password » pour le choisir vous-même)."
        fi

        info "Création de la clé (RSA 4096, valable 10 000 jours)"
        # Le mot de passe passe par l'environnement : « -storepass » l'exposerait
        # dans la liste des processus. En PKCS12, la clé partage celui du keystore.
        KEYSTORE_PASSWORD="$PASSWORD" keytool -genkeypair \
            -keystore "$KEYSTORE" -storetype PKCS12 \
            -alias "$ALIAS" \
            -keyalg RSA -keysize 4096 -validity 10000 \
            -dname "CN=Demi Course" \
            -storepass:env KEYSTORE_PASSWORD >/dev/null
        chmod 600 "$KEYSTORE"
        ok "Clé créée : $KEYSTORE (alias « $ALIAS »)"
        write_props "$KEYSTORE" "$PASSWORD" "$ALIAS"
    fi

else
    [[ -f "$PROPS" ]] || die "${PROPS#"$ROOT_DIR"/} introuvable : rien à publier. \
Relancez sans « --secrets-only » pour créer la clé."
    KEYSTORE="$(read_prop storeFile)"
    PASSWORD="$(read_prop storePassword)"
    ALIAS="$(read_prop keyAlias)"
    [[ -n "$KEYSTORE" && -n "$PASSWORD" && -n "$ALIAS" ]] || die "${PROPS#"$ROOT_DIR"/} est incomplet."
    [[ -f "$KEYSTORE" ]] || die "keystore introuvable : $KEYSTORE"
    keystore_opens "$KEYSTORE" "$PASSWORD" "$ALIAS" \
        || die "le mot de passe de ${PROPS#"$ROOT_DIR"/} n'ouvre pas « $KEYSTORE » (alias « $ALIAS »)."
    ok "Clé : $KEYSTORE (alias « $ALIAS »)"
fi

# Le .gitignore couvre *.jks et keystore.properties ; un chemin exotique, non.
if [[ $DO_KEY -eq 1 || $DO_SECRETS -eq 1 ]] && [[ "$KEYSTORE" == "$ROOT_DIR"/* ]]; then
    if ! (cd "$ROOT_DIR" && git check-ignore -q "$KEYSTORE") 2>/dev/null; then
        warn "« $KEYSTORE » n'est pas ignoré par git — ne le committez pas."
    fi
fi

# --- secrets ----------------------------------------------------------------

if [[ $DO_SECRETS -eq 0 ]]; then
    printf '%sClé prête ; secrets non publiés (« --no-secrets »).%s\n' "$C_DIM" "$C_RESET"
    exit 0
fi

# Un base64 sur une seule ligne, avec ou sans l'option -w de GNU coreutils.
encode_base64() {
    base64 -w0 "$1" 2>/dev/null || base64 "$1" | tr -d '\n'
}

set_secret() {
    local name="$1" value="$2" output
    [[ -n "$value" ]] || die "valeur vide pour $name : rien n'a été publié."
    # La sortie de gh est gardée pour la montrer en cas d'échec : un « HTTP 403 »
    # ou un scope manquant doit se voir, pas disparaître dans /dev/null.
    if ! output="$(printf '%s' "$value" | gh secret set "$name" --repo "$REPO" 2>&1)"; then
        [[ -n "$output" ]] && printf '%s\n' "$output" >&2
        die "échec de la publication de $name. Si gh parle d'autorisation, \
« gh auth refresh -h github.com -s repo » donne au jeton le droit d'écrire les secrets."
    fi
    ok "$name (${#value} caractères)"
}

info "Dépôt cible : $REPO"

existing="$(gh secret list --repo "$REPO" 2>/dev/null | awk '{ print $1 }' || true)"
if grep -qx 'ANDROID_KEYSTORE_BASE64' <<<"$existing"; then
    warn "le secret ANDROID_KEYSTORE_BASE64 existe déjà et va être écrasé."
    if [[ $FORCE -eq 0 ]]; then
        if { : < /dev/tty; } 2>/dev/null; then
            read -rp "Continuer ? [o/N] " confirm < /dev/tty
            [[ "$confirm" == [oOyY] ]] || die "annulé."
        else
            die "sans terminal interactif, relancez avec « --force » pour écraser les secrets."
        fi
    fi
fi

info "Publication des secrets"
set_secret ANDROID_KEYSTORE_BASE64   "$(encode_base64 "$KEYSTORE")"
set_secret ANDROID_KEYSTORE_PASSWORD "$PASSWORD"
set_secret ANDROID_KEY_ALIAS         "$ALIAS"
set_secret ANDROID_KEY_PASSWORD      "$PASSWORD"

info "Vérification"
published="$(gh secret list --repo "$REPO" 2>/dev/null | awk '{ print $1 }' || true)"
missing=()
for name in ANDROID_KEYSTORE_BASE64 ANDROID_KEYSTORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD; do
    grep -qx "$name" <<<"$published" || missing+=("$name")
done
if [[ ${#missing[@]} -gt 0 ]]; then
    die "absents de la liste des secrets de $REPO : ${missing[*]}. \
Vérifiez le dépôt visé et les droits du jeton (« gh auth status »)."
fi
ok "les quatre secrets sont bien enregistrés sur $REPO"
printf '%s  GitHub n'"'"'affiche jamais la valeur d'"'"'un secret, seulement son nom et sa date.%s\n' \
    "$C_DIM" "$C_RESET"

cat <<EOF

${C_BOLD}Terminé.${C_RESET} Le prochain merge sur main publiera une release avec un APK signé.

${C_YELLOW}À sauvegarder hors du dépôt${C_RESET} (gestionnaire de mots de passe, disque chiffré) :
  • $KEYSTORE
  • $PROPS

Sans eux, plus aucune mise à jour ne pourra être installée par-dessus
l'application déjà présente sur les téléphones.
EOF
