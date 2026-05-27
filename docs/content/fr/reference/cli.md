---
title: CLI
summary: L'outil en ligne de commande juicer — build, serve, config, theme.
weight: 10
---

La CLI de juicer a quatre sous-commandes : `build`, `serve`, `config` et
`theme` (avec les sous-sous-commandes `add` / `upgrade`). Chaque commande
s'exécute sur JVM, Scala.js (Node) et Scala Native — `serve` et son
observateur de rechargement à chaud utilisent [microserve](https://github.com/edadma/microserve)
pour le HTTP et la surveillance de fichiers multiplateformes.

## Options globales

Celles-ci s'appliquent à **chaque** sous-commande. Placez-les avant le nom de la
sous-commande sur la ligne de commande :

| Option                  | Quoi |
|-------------------------|------|
| `-b`, `--baseurl <URL>` | Supplante `baseURL` de la configuration — typiquement utilisé en CI pour des URL propres à l'environnement (`https://staging.example.com/` vs `https://example.com/`). |
| `-c`, `--config <name>` | Choisit une configuration de base : `simple`, `standard` (défaut) ou `norme`. Votre `site.toml` se superpose par-dessus la base choisie. |
| `-h`, `--help`          | Affiche l'usage et quitte. |
| `-v`, `--verbose`       | Affiche la progression de construction étape par étape — sélection de layout, parcours de fichiers, ce qui est écrit où. Indispensable quand une page est silencieusement sautée. |
| `--version`             | Affiche la version et quitte. |

```bash
sbt 'juicerJVM/run --baseurl https://staging.example.com/ build -s docs'
sbt 'juicerJVM/run --config simple build -s notes'
sbt 'juicerJVM/run -v build -s docs'
```

## `build`

Rend le site sur le disque. La sortie va dans `<src>/<publicDir>` (par défaut
`<src>/public/`) à moins que `-d` ne la supplante.

```bash
sbt 'juicerJVM/run build -s docs'
sbt 'juicerJVM/run build -s docs -d _site -b https://juicer.build/'
sbt 'juicerJVM/run build -s blog -D -F'
```

| Option                | Défaut               | Quoi |
|-----------------------|----------------------|------|
| `-s`, `--source <p>`  | `.`                  | Répertoire source du site. |
| `-d`, `--dest <p>`    | `<src>/<publicDir>`  | Répertoire de sortie. Supplante la clé de configuration `publicDir`. |
| `-D`, `--drafts`      | désactivé            | Inclut les pages avec un frontmatter `draft: true`. Les brouillons sont normalement invisibles à chaque consommateur en aval — TDM, sitemap, index de recherche, listings de section. |
| `-F`, `--future`      | désactivé            | Inclut les pages dont la `date:` analysée est au-delà du moment de construction courant. Le saut du futur ne s'applique qu'aux pages avec un frontmatter `date:` **explicite** — les dates issues du repli sur la mtime ne sont jamais sautées comme futures. Les pages dans l'`eventsSection` (par défaut `events`) sont aussi exemptées du saut du futur. |

[= tip =]
La construction CI classique, p. ex. pour un workflow GitHub Pages :

```bash
sbt 'juicerJVM/run build -s docs -d _site -b https://example.github.io/repo/'
```
[= /tip =]

## `serve`

Construit le site une fois, puis le sert sur `localhost`. Surveille
éventuellement la source et reconstruit à chaque changement.

```bash
sbt 'juicerJVM/run serve -s docs'
sbt 'juicerJVM/run serve -s docs -L'
sbt 'juicerJVM/run serve -s docs --host 0.0.0.0 -p 4000'
```

| Option                | Défaut               | Quoi |
|-----------------------|----------------------|------|
| `-s`, `--source <p>`  | `.`                  | Répertoire source du site. |
| `-d`, `--dest <p>`    | `<src>/<publicDir>`  | Répertoire de sortie. Exclu de la boucle de surveillance du rechargement à chaud pour que les constructions ne se déclenchent pas elles-mêmes. |
| `--host <h>`          | `localhost`          | Hôte de liaison. Utilisez `0.0.0.0` pour exposer le serveur de dev sur le réseau local. |
| `-p`, `--port <p>`    | `8080`               | Port d'écoute. Si le port est déjà occupé, juicer balaie jusqu'à 20 ports vers le haut (`:8081`, `:8082`, …) et affiche le port réel sur lequel il a atterri. |
| `-D`, `--drafts`      | désactivé            | Inclut les brouillons (mêmes sémantiques que `build -D`). |
| `-F`, `--future`      | désactivé            | Inclut les pages datées dans le futur (mêmes sémantiques que `build -F`). |
| `-L`, `--live-reload` | désactivé            | Surveille la source récursivement, reconstruit au changement et recharge à chaud les onglets de navigateur connectés. |

### Rechargement à chaud — comment ça marche

Quand `-L` est activé, chaque réponse HTML reçoit un minuscule script client de
sondage long injecté avant `</body>`. Le script appelle
`GET /__juicer/wait?since=<version>` ; le serveur maintient chaque sondage ouvert
jusqu'à la prochaine reconstruction (ou 30 s, selon ce qui vient en premier) et
répond avec `{reload, version}`. Le client appelle `location.reload()` sur
`reload: true`.

Le sondage long — pas le SSE — a été choisi parce que le SSE fuit une connexion
lors du déchargement de page selon les navigateurs, et le regroupement par limite
de connexions HTTP/1.1 fait que le mode de défaillance ressemble à « le
rechargement à chaud a silencieusement cessé de fonctionner ». Un `fetch` neuf par
cycle n'a ni l'un ni l'autre problème.

L'observateur est anti-rebond à 150 ms, donc enregistrer cinq fichiers en succession
rapide donne une seule reconstruction et un seul rechargement. Les enregistrements
vers le répertoire de sortie (`-d`) sont ignorés pour éviter les boucles de
construction-qui-se-déclenche-elle-même.

## `config`

Affiche la configuration résolue après fusion de la base choisie, de la superposition
`site.toml` et des surcharges CLI. Utile pour déboguer « pourquoi `htmlDir` fait-il
ça ? » — la réponse est juste là dans la configuration affichée.

```bash
sbt 'juicerJVM/run config -s .'
sbt 'juicerJVM/run --config simple config -s notes'
```

| Option                | Défaut  | Quoi |
|-----------------------|---------|------|
| `-s`, `--source <p>`  | `.`     | Répertoire source du site. |

## `theme add`

Installe un thème depuis un dépôt git dans `<src>/<themeDir>/`. Après l'avoir
exécuté, définissez `theme = "<name>"` dans votre `site.toml` pour l'activer.

```bash
sbt 'juicerJVM/run theme add https://github.com/edadma/juicer.git -n juicerdocs'
sbt 'juicerJVM/run theme add https://github.com/me/my-theme.git -r v1.2.0'
sbt 'juicerJVM/run theme add https://github.com/edadma/juicer.git -n juicerblog --subdir docs/themes/juicerblog'
```

| Option                        | Défaut                  | Quoi |
|-------------------------------|-------------------------|------|
| `<git-url>` (positionnel)     | requis                  | URL HTTPS ou SSH du dépôt du thème. |
| `-s`, `--source <p>`          | `.`                     | Répertoire source du site. Le thème est installé sous `<src>/<themeDir>/`. |
| `-n`, `--name <name>`         | déduit de l'URL/sous-rép | Nom de répertoire sous lequel installer. Requis quand juicer ne peut pas déduire un nom sensé depuis l'URL. |
| `-r`, `--ref <ref>`           | HEAD du dépôt           | Branche, étiquette ou SHA de commit à extraire. Verrouillez sur une étiquette pour des constructions reproductibles. |
| `--subdir <path>`             | racine du dépôt         | N'installe que ce sous-répertoire du dépôt cloné. Utile quand un dépôt livre plusieurs thèmes (p. ex. juicer livre les six sous `docs/themes/`). |
| `--force`                     | désactivé               | Écrase un répertoire de thème existant. Sans cela, un `<themeDir>/<name>/` existant avorte l'installation. |

Les métadonnées pour un `upgrade` ultérieur (URL, ref, sous-rép) sont enregistrées
dans `<themeDir>/<name>/.juicer-theme.toml`. Commiter ce fichier ou non vous revient
— il est petit, c'est du TOML brut, et le commiter signifie que les collaborateurs
obtiennent la même version de thème après `git pull` sans réexécuter `theme add`.

[= note =]
**`theme add` n'installe que le répertoire du thème.** Vous devez encore définir
`theme = "<name>"` dans `site.toml` pour l'activer — juicer ne devine pas lequel de
vos thèmes installés vous voulez actif. Exécutez `juicer config -s .` après avoir
édité `site.toml` pour vérifier la valeur `theme` résolue.
[= /note =]

## `theme upgrade`

Re-récupère un ou chaque thème installé depuis l'URL+ref enregistrée dans son
`.juicer-theme.toml`. Utilisez ceci après que le thème amont a livré un correctif et
que vous voulez l'intégrer.

```bash
sbt 'juicerJVM/run theme upgrade'
sbt 'juicerJVM/run theme upgrade juicerblog'
sbt 'juicerJVM/run theme upgrade juicerblog -r v1.3.0'
```

| Option                        | Défaut         | Quoi |
|-------------------------------|----------------|------|
| `<name>` (positionnel)        | chaque thème   | Quel thème mettre à niveau. Omis = met à niveau chaque thème qui a un `.juicer-theme.toml`. |
| `-s`, `--source <p>`          | `.`            | Répertoire source du site. |
| `-r`, `--ref <ref>`           | ref enregistrée | Supplante la ref enregistrée pour cette mise à niveau seulement. Ne réécrit pas `.juicer-theme.toml` — le prochain `theme upgrade` nu revient à la valeur précédemment enregistrée à moins que vous ne réexécutiez aussi `theme add`. |

## Codes de sortie

| Code | Signification |
|------|---------------|
| `0`  | Succès. |
| `1`  | Tout échec — mauvais chemin source, configuration requise manquante, erreur d'analyse dans `site.toml`, erreur de rendu de gabarit, échec git dans `theme add`/`upgrade`. Exécutez avec `-v` pour la trace de pile détaillée. |
