# Changelog

Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/). Les releases
sont versionnées `yyyy.MM.dd-N` (date de publication + rang du jour), pas en SemVer :
voir `CLAUDE.md` § Variantes et versions.

## [Non publié]

### Ajouté
- Équipe d'agents spécialistes (`.claude/agents/`), CI de vérification des PR (lint ktlint, tests, build, scan de secrets) avec compte rendu commenté, Dependabot, `.editorconfig`.
- Libellés TalkBack, rôles et cibles tactiles de 48 dp, libellés de champs, bordures de focus et d'erreur, confirmation avant réinitialisation de la séance.
- Somme SHA-256 publiée avec l'APK de chaque release.

### Modifié
- Palette : contrastes de `muted2`/`muted3` relevés pour atteindre 4,5 : 1.
- Actions GitHub épinglées par SHA, permissions des workflows réduites au strict nécessaire.
- L'état enregistré est lu une seule fois au démarrage (plus d'écrasement par le flux DataStore).

### Corrigé
- Un nombre de répétitions saisi très grand figeait l'application à chaque lancement : plafond à 99 partout.
- `autoName` renvoyait « km » seul pour une distance inférieure à 5 m.
- Les copies de sauvegarde d'une ancienne clé de signature ne sont plus visibles de git.

## 2026-11 — avant ce journal

### Ajouté
- Séance de course : étapes (allure unique ou intervalle, distance ou durée, répétitions, récupération), modèles réutilisables, calcul du point de demi-tour par distance ou par durée, thèmes clair/sombre/système, persistance locale (DataStore).
- Reste de l'effort ou de la récupération au point de demi-tour, durées affichées en `3m02s`.
- Variantes dev (`.dev`, nom « Demi Course (dev) ») et prod ; release GitHub déclenchée manuellement avec APK signé ; scripts `deploy.sh` et `setup-signing.sh`.
- Icône « piste » sur fond clair avec calque monochrome.

### Modifié
- Feuille d'édition scrollable, suivant le champ actif.
