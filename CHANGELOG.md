# Changelog

Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Les releases
sont versionnées `yyyy.MM.dd-N` (date de publication + rang du jour), pas en SemVer :
voir `CLAUDE.md` § Variantes et versions.

## [Non publié]

### Ajouté
- Équipe d'agents spécialistes (`.claude/agents/`), CI de vérification des PR avec compte rendu commenté, `.editorconfig`.

## 2026-11 — avant ce journal

### Ajouté
- Séance de course : étapes (allure unique ou intervalle, distance ou durée, répétitions, récupération), modèles réutilisables, calcul du point de demi-tour par distance ou par durée, thèmes clair/sombre/système, persistance locale (DataStore).
- Reste de l'effort ou de la récupération au point de demi-tour, durées affichées en `3m02s`.
- Variantes dev (`.dev`, nom « Demi Course (dev) ») et prod ; release GitHub déclenchée manuellement avec APK signé ; scripts `deploy.sh` et `setup-signing.sh`.
- Icône « piste » sur fond clair avec calque monochrome.

### Modifié
- Feuille d'édition scrollable, suivant le champ actif.
