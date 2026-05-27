---
title: Dépannage
summary: Les problèmes courants d'une installation neuve de juicer — et le correctif en une ligne pour chacun.
weight: 30
---

Cette page est organisée par **symptôme**, pas par fonctionnalité. Si quelque chose ne fonctionne pas, parcourez les titres à la recherche de ce que vous voyez à l'écran, puis lisez la cause et le correctif ci-dessous.

## Construction / installation

### `sbt 'juicerJVM/run --help'` échoue avant d'afficher la bannière d'aide

**Cause.** Soit sbt lui-même ne démarre pas (version de Java trop ancienne ; sbt n'a pas pu récupérer ses plugins), soit la construction de juicer est cassée (modifications non validées de `build.sbt`, dépendance manquante).

**Correctif.** Vérifiez que `java -version` indique JDK 17 ou plus récent ; vérifiez que `sbt sbtVersion` indique 1.12.x. Puis, depuis la racine du dépôt :

```bash
sbt clean compile
```

Si `compile` réussit mais pas `run`, le problème est la résolution des dépendances — essayez `sbt update` pour les récupérer à nouveau depuis Maven Central.

### `not a readable directory: <src>`

**Cause.** Le chemin `-s <src>` n'existe pas, n'est pas un répertoire, ou vous n'avez pas la permission de lecture. Souvent une faute de frappe ou une exécution depuis le mauvais répertoire courant.

**Correctif.** Utilisez un chemin absolu ou `pwd && ls <src>` pour confirmer que le répertoire existe depuis l'endroit où vous lancez sbt. La plupart des commandes juicer s'exécutent relativement au répertoire courant, donc `sbt 'juicerJVM/run build -s docs/demos/juicerblog'` ne fonctionne que depuis la racine du dépôt.

### `unknown base configuration: <name>`

**Cause.** Le drapeau `--config` référence une configuration de base qui n'existe pas. Les valeurs valides sont `simple`, `standard` (par défaut) et `norme`.

**Correctif.** Retirez le drapeau — `standard` convient à 99 % des sites. Si vous avez besoin d'une autre forme, voir [Configuration](/fr/reference/config/).

### Erreurs de `compile` après un `git pull`

**Cause.** Une dépendance est passée à une version qui nécessite un rafraîchissement de `~/.ivy2/cache`, ou un fichier source Scala a changé d'une manière que la compilation incrémentale de sbt n'a pas détectée.

**Correctif.** `sbt clean` puis relancez. Si le problème vient d'un changement de dépendance, `sbt update` récupère d'abord les nouveaux artefacts avant que `compile` ne reconstruise.

## Gabarits

### `layout '<X>' not found for rendering '<page>'` dans la sortie verbeuse

**Cause.** Soit le thème ne fournit pas de gabarit portant ce nom, soit le frontmatter `layout:` d'une page en référence un qui n'existe pas. Le plus souvent c'est une faute de frappe (`layout: hime` au lieu de `layout: home`).

**Correctif.** Vérifiez le répertoire `layouts/_default/` du thème pour le fichier que vous nommez. Les thèmes peuvent se passer de certains gabarits (juicerblog fournit `home.html` et `archive.html` ; juicerdocs non). La construction n'échoue pas — elle retombe sur `file.html` / `folder.html` — mais la page s'affiche avec l'habillage par défaut plutôt qu'avec le gabarit voulu.

### Une page s'affiche avec le mauvais gabarit

**Cause.** Le frontmatter `layout: foo` est pris en compte alors que vous ne vouliez pas le définir, ou l'inverse.

**Correctif.** Inspectez le frontmatter de la page — `layout:` est la dérogation ; retirez-le pour retomber sur le défaut du thème. Voir [Fichiers de contenu](/fr/concepts/content-files/) pour les règles de sélection de gabarit.

### Un `_index.md` s'affiche comme un seul article au lieu d'une section

**Cause.** Le fichier n'est pas nommé `_index.md` (notez le tiret bas) ou se trouve dans le mauvais répertoire. Les pages de section DOIVENT être nommées exactement `_index.md` et résider à la racine de leur répertoire de section.

**Correctif.** Renommez `index.md` → `_index.md` et confirmez qu'il est à la racine de la section, pas imbriqué dans un sous-répertoire.

## Thèmes

### Le thème s'affiche sans aucun style

**Cause.** Le fichier CSS du thème n'est pas copié vers la sortie, OU le `<link rel=stylesheet>` référence un chemin qui ne se résout pas.

**Correctif.** Vérifiez d'abord que le `static/<theme>.css` du thème a été copié — `ls <dst>/<theme>.css`. S'il n'y est pas, vos clés `themeDir` / `theme` dans `site.toml` ne pointent pas vers le bon répertoire. Lancez avec `-v` et cherchez une ligne `theme: …` ; le chemin qu'elle journalise est celui que juicer utilise réellement.

### `theme add <git-url>` échoue avec « could not derive theme name »

**Cause.** L'URL git ne se termine pas par quelque chose d'utile (`.git`, `repo-name`). Fréquent avec des hôtes git personnalisés ou des formes d'URL inhabituelles.

**Correctif.** Passez `-n <name>` explicitement : `juicer theme add https://example.com/weird-url -n my-theme`.

### Un nouveau thème n'apparaît pas après `theme add`

**Cause.** Vous avez oublié de mettre à jour `site.toml` — `theme add` ne fait que cloner le répertoire ; il ne change pas le thème actif.

**Correctif.** Ajoutez `theme = "<name>"` à `site.toml`. Vérifiez avec `juicer config -s <src>` — la valeur `theme` active s'affiche près du début.

### Les ressources du site (logo, favicon) supplantent celles du thème, mais elles n'apparaissent pas

**Cause.** Les fichiers du site l'emportent BIEN sur ceux du thème, mais seulement quand le fichier réside au MÊME chemin relatif. Un `<src>/static/logo.png` du site supplante un `themes/X/static/logo.png` du thème — mais `<src>/img/logo.png` non, parce que le thème attend `static/`.

**Correctif.** Reproduisez exactement la structure de répertoires du thème. Voir [Thèmes](/fr/concepts/themes/) pour les règles de superposition.

## Fonctionnalités de blogue

### Les blocs de code ne sont pas colorés

**Cause.** Aucun répertoire `<src>/grammars/` n'existe, ou le fichier `*.tmLanguage.json` qu'il contient ne correspond pas à l'étiquette de langage de votre bloc. Un bloc ouvert par ` ```scala ` cherche exactement `grammars/scala.tmLanguage.json` — sensible à la casse.

**Correctif.** Créez `<src>/grammars/`, déposez-y le JSON de grammaire pour chaque langage que vous utilisez. Voir [juicerblog → Configuration](/themes/juicerblog/configuration/#syntax-highlighting) pour la configuration complète. Lancez avec `-v` et cherchez la ligne `highlighter: N grammar(s) loaded` — si vous ne la voyez pas, le répertoire est manquant ou vide.

### Le code coloré n'a pas de couleurs

**Cause.** La grammaire s'est chargée, mais votre thème n'a pas de CSS pour les classes `.hl-*` que le coloriseur émet.

**Correctif.** Les thèmes qui fournissent une palette (juicerblog, juicerdocs) gèrent cela automatiquement. Pour un thème fait main, copiez le bloc `.hl-keyword` / `.hl-string` / `.hl-comment` / etc. depuis `docs/themes/juicerblog/static/juicerblog.css` et adaptez les couleurs.

### Les archives par date `/2024/` etc. ne sont pas émises

**Cause.** Soit `dateArchives = true` n'est pas défini dans `site.toml`, SOIT le thème ne fournit pas `_default/date-year.html` et `date-month.html` (gabarit manquant = ignoré silencieusement), SOIT aucun article n'a de `date:` explicite analysable dans son frontmatter.

**Correctif.** Confirmez les trois :

1. `grep dateArchives <src>/site.toml` renvoie `dateArchives = true`.
2. Le thème a les gabarits : `ls <theme>/layouts/_default/date-*.html`.
3. Au moins un article a `date: 2024-MM-DD` (ou similaire) dans son frontmatter — les pages dont la date provient du repli sur le mtime du système de fichiers sont volontairement exclues.

### Les pages de mots-clés n'incluent pas un article qui porte le mot-clé

**Cause.** Soit l'article a le frontmatter `static: true` (filtré de toutes les listes d'articles), soit le slug du mot-clé ne correspond pas à ce que vous avez tapé dans l'URL. Les mots-clés sont slugifiés (mis en minuscules, repliés en ASCII, les suites de caractères non alphanumériques réduites à `-`).

**Correctif.** Visitez `/tags/` pour voir le slug canonique que juicer a dérivé. Si votre article a `static: true`, c'est la raison — voir [juicerblog → Configuration](/themes/juicerblog/configuration/#per-page-frontmatter).

### La page d'archive d'un auteur est vide même si l'auteur a des articles

**Cause.** La valeur du frontmatter `author:` (ou `authors:`) ne correspond à aucun `id` du registre `[[authors]]`. juicer retombe sur une ébauche `{id: <typo>}` pour que les gabarits ne plantent pas, mais l'archive est vide.

**Correctif.** Comparez la sortie de `grep "^author:" content/*.md` aux valeurs `id =` de vos blocs `[[authors]]` dans `site.toml` — les fautes de frappe sont le coupable habituel.

### Les permaliens `[permalinks]` ne s'appliquent pas

**Cause.** Le nom de section dans `[permalinks]` ne correspond pas au répertoire de contenu. `[permalinks] posts = ":year/:slug/"` ne s'applique qu'aux pages sous `content/posts/` — les articles à la racine de `content/` ne sont pas affectés.

**Correctif.** Soit déplacez les articles sous `content/posts/` (recommandé pour les blogues), soit listez les noms de section que vous avez réellement dans `[permalinks]`.

### Les alias redirigent au mauvais endroit

**Cause.** La liste du frontmatter `aliases:` contient la mauvaise forme — généralement un `/` initial ou final manquant.

**Correctif.** Chaque alias est un chemin d'URL avec une barre oblique au début et à la fin : `aliases: [/old-name/, /even-older/]`. Vérifiez le HTML d'alias rendu à `<dst>/<alias>/index.html` pour voir quelle URL de redirection a été inscrite.

## Serveur / rechargement à chaud

### `juicer serve` échoue avec « address already in use »

**Cause.** Un autre processus (souvent un `juicer serve` précédent que vous avez oublié d'arrêter) occupe le port.

**Correctif.** Passez un autre port : `-p 8089`. La boucle de liaison-avec-réessai de juicer balaie aussi 20 ports à partir du défaut, donc `-p 8088` trouvera le suivant libre si 8088 est pris — surveillez le port réel dans la bannière de démarrage.

### Le rechargement à chaud ne se déclenche pas quand j'enregistre un fichier markdown

**Cause.** Le drapeau `-L` (ou `--live-reload`) n'a pas été passé, OU votre navigateur a cessé d'interroger le point d'accès de rechargement, OU le fichier enregistré est hors du répertoire surveillé.

**Correctif.** Confirmez que la bannière de démarrage indique `live reload: enabled`. Ouvrez les outils de développement du navigateur → Réseau et rechargez la page ; vous devriez voir un `fetch` de longue durée vers `/__juicer/wait?since=N` (le script de rechargement est injecté avant `</body>` dans chaque réponse HTML). Les enregistrements sont temporisés de 150 ms — n'attendez pas que des enregistrements plus rapides se regroupent, mais tout ce qui est plus lent devrait recharger en une seconde. Si la page n'a pas été ouverte via le serveur de développement (p. ex. une URL `file://` ou un onglet en cache périmé), le script ne s'est jamais chargé et les rechargements ne se déclenchent jamais — ouvrez la page via `http://localhost:<port>/`.

### Le navigateur affiche l'ancien contenu même après un enregistrement

**Cause.** Le cache du navigateur conserve la page même si juicer l'a reconstruite. Le `serve` de juicer envoie des en-têtes `Cache-Control: no-cache` quand `-L` est actif, mais un service worker périmé ou un proxy CDN agressif peut passer outre.

**Correctif.** Rechargement forcé (Cmd-Maj-R / Ctrl-Maj-R). Si cela ne corrige pas, outils de développement → Application → Service Workers → désinscrire.

## Gabarits (templates)

### `unknown variable: X` au moment de la construction

**Cause.** Une référence `{{ X }}` pointe vers une variable qui n'est pas dans la portée à ce point du rendu.

**Correctif.** La plupart des variables proviennent de trois sources : `.page.<...>` (frontmatter par page + champs calculés), `.site.<...>` (configuration du site + index globaux), ou des affectations locales `:=`. Consultez [Données de gabarit](/fr/reference/template-data/) pour les formes disponibles. Un piège courant dans les boucles `for` : `.foo` se résout par rapport à l'élément itéré courant, pas à la portée externe — utilisez `$.foo` pour remonter à la racine des données.

### `not a number: <X> (java.lang.Y)` dans les comparaisons de gabarit

**Cause.** Les opérateurs relationnels de squiggly (`< > <= >= = !=`) exigent que les deux côtés soient numériques. Un type Java encadré peut échouer à se convertir s'il a une forme inhabituelle.

**Correctif.** Passé à squiggly 0.2.4 ? Cette version a ajouté la coercition des Number encadrés. Les versions antérieures trébuchaient sur les indices `for x, i <- coll`. `cd <repo>; grep squiggly build.sbt` devrait indiquer 0.2.4 ou plus récent.

### Le contenu du corps markdown ne rend pas ses boucles `{{ for ... }}`

**Cause.** **Intentionnel**, pas un bogue. Les gabarits squiggly à l'intérieur d'un corps markdown sont échappés en HTML avant le rendu — ils sont traités comme du texte littéral. Seuls les fichiers de `layouts/` et `partials/` exécutent des gabarits.

**Correctif.** Déplacez la boucle dans un gabarit ou un partial, et faites en sorte que le markdown l'appelle via un shortcode ou en étant assez court pour vivre directement dans le gabarit.

## Quand rien ne fonctionne

Lancez avec `-v` (verbeux) pour voir chaque étape de la construction — parcours des fichiers, sélection des gabarits, ce qui est écrit et où :

```bash
sbt 'juicerJVM/run build -s <src> -v'
```

Si quelque chose est ignoré silencieusement (gabarit manquant, page filtrée, etc.), la sortie verbeuse montre généralement la ligne où ça s'est arrêté. Collez la portion pertinente dans une [issue GitHub](https://github.com/edadma/juicer/issues) avec le plus petit cas reproductible qui la fait apparaître.
