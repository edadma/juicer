---
title: Shortcodes
summary: Chaque shortcode fourni avec le thème juicerdocs, avec des exemples copiables-collables.
weight: 60
---

Les [shortcodes](/fr/concepts/shortcodes/) sont des gabarits invoqués depuis
l'intérieur du markdown avec la syntaxe `\[= name =]`. Le thème juicerdocs en
livre 16, couvrant les encadrés, les blocs de documentation structurés, les
intégrations et les atomes en ligne. Cette page est le catalogue — une entrée par
shortcode avec la syntaxe exacte, le rendu visuel et les arguments qu'il accepte.

[= note =]
**Ce sont les shortcodes de juicerdocs.** Ils résident sous
`docs/themes/juicerdocs/shortcodes/`. Si vous utilisez un autre thème, vous
n'obtiendrez pas cet ensemble — apportez le vôtre, copiez-en individuellement dans
`<src>/shortcodes/`, ou incluez `juicerdocs` dans votre chaîne de thèmes. Voir
[Concepts → Shortcodes](/fr/concepts/shortcodes/) pour les conventions d'appel et
comment rédiger les vôtres.
[= /note =]

[= tip =]
**Échappement dans cette page.** Les exemples en bloc de code ci-dessous utilisent
`\[=` (préfixé d'une barre oblique inverse) pour que le préprocesseur laisse la
syntaxe de shortcode littérale en place plutôt que de l'étendre. Quand vous écrivez
des shortcodes dans votre propre contenu, retirez la barre oblique inverse.
[= /tip =]

## Encadrés

Cinq variantes d'admonition, chacune un shortcode apparié qui enveloppe un corps
markdown. Elles partagent leur style : un fond teinté, une petite icône, un
rembourrage généreux.

### `\[= note =]`

Information neutre. À utiliser pour les apartés « au fait » que le lecteur peut
sauter sans rien manquer d'essentiel.

```markdown
\[= note =]
The frontmatter format is YAML. JSON and TOML frontmatter are
**not** supported.
\[= /note =]
```

[= note =]
Le format de frontmatter est YAML. Le frontmatter JSON et TOML n'est **pas** pris en charge.
[= /note =]

### `\[= tip =]`

Une suggestion ou un raccourci. À utiliser pour le contenu « vous pouvez aussi … »
où le chemin alternatif est parfois le meilleur.

```markdown
\[= tip =]
The `juicer config` subcommand prints the merged config — it's
the fastest way to debug "why is this key not doing what I expect?"
\[= /tip =]
```

[= tip =]
La sous-commande `juicer config` affiche la configuration fusionnée — c'est le moyen
le plus rapide de déboguer « pourquoi cette clé ne fait-elle pas ce que j'attends ? »
[= /tip =]

### `\[= warning =]`

Quelque chose qui vaut la peine de ralentir. À utiliser quand ignorer cette
information produira une défaillance déroutante plus tard.

```markdown
\[= warning =]
Permalink templates change both the URL and the on-disk write
location. Juicer doesn't keep both copies.
\[= /warning =]
```

[= warning =]
Les gabarits de permalien changent à la fois l'URL et l'emplacement d'écriture sur
le disque. Juicer ne garde pas les deux copies.
[= /warning =]

### `\[= danger =]`

Une mise en garde sérieuse. À utiliser pour « ceci va casser des choses » —
opérations destructrices, mises en garde de sécurité, options irréversibles.

```markdown
\[= danger =]
Never expose a shortcode that takes user input straight into HTML.
Run it through `markdownify` or escape it explicitly.
\[= /danger =]
```

[= danger =]
N'exposez jamais directement dans le HTML un shortcode qui prend une entrée
utilisateur. Passez-la par `markdownify` ou échappez-la explicitement.
[= /danger =]

### Choisir le bon encadré

Une règle empirique : `note` pour l'information, `tip` pour un meilleur chemin,
`warning` pour un piège, `danger` pour quelque chose qui cassera activement.

## Blocs structurés

### `\[= steps =]`

Étapes numérotées, disposées verticalement. Les titres à l'intérieur (`## Nom de
l'étape`) sont auto-numérotés ; le corps de chaque étape est du markdown ordinaire.

```markdown
\[= steps =]
## Install Juicer

Clone the repo and verify with `sbt 'juicerJVM/run --help'`.

## Make a site directory

Create `site.toml`, a `content/` folder, and `layouts/_default/`.

## Build & serve

Run `sbt 'juicerJVM/run serve -s .'` and open <http://localhost:8080>.
\[= /steps =]
```

[= steps =]
## Installer Juicer

Clonez le dépôt et vérifiez avec `sbt 'juicerJVM/run --help'`.

## Créer un répertoire de site

Créez `site.toml`, un dossier `content/` et `layouts/_default/`.

## Construire et servir

Exécutez `sbt 'juicerJVM/run serve -s .'` et ouvrez <http://localhost:8080>.
[= /steps =]

### `\[= tabs =]` / `\[= tab "Label" =]`

Volets de contenu à onglets. Le `tabs` externe est un conteneur ; chaque `tab`
prend un libellé comme argument positionnel et un corps markdown.

```markdown
\[= tabs =]
\[= tab "JVM" =]
sbt 'juicerJVM/run build -s docs'
\[= /tab =]
\[= tab "Native" =]
juicer build -s docs
\[= /tab =]
\[= /tabs =]
```

[= tabs =]
[= tab "JVM" =]
```bash
sbt 'juicerJVM/run build -s docs'
```
[= /tab =]
[= tab "Native" =]
```bash
juicer build -s docs
```
[= /tab =]
[= tab "Node" =]
```bash
node ./juicer-cli.js build -s docs
```
[= /tab =]
[= /tabs =]

### `\[= filetree =]`

Rend le contenu intérieur dans une boîte à chasse fixe. À utiliser pour les
listings de répertoires en arbre — les caractères de dessin de boîtes se collent tels quels.

```markdown
\[= filetree =]
my-site/
├── content/
│   ├── _index.md
│   └── about.md
└── site.toml
\[= /filetree =]
```

[= filetree =]
my-site/
├── content/
│   ├── _index.md
│   └── about.md
├── layouts/
│   └── _default/
│       ├── file.html
│       └── folder.html
└── site.toml
[= /filetree =]

### `\[= collapse "Summary" =]`

`<details>` / `<summary>` natif avec le style juicerdocs. L'argument positionnel
est le texte de résumé ; le corps est le contenu replié (le markdown y est rendu).

```markdown
\[= collapse "Why no JSON frontmatter?" =]
YAML covers the use case fluently, and supporting three formats
triples the surface area to keep correct.
\[= /collapse =]
```

[= collapse "Pourquoi pas de frontmatter JSON ?" =]
YAML couvre le cas d'usage avec aisance, et prendre en charge trois formats triple
la surface à maintenir correcte.
[= /collapse =]

## Atomes en ligne

Shortcodes auto-fermants pour de courtes fioritures d'UI en ligne.

### `\[= kbd "Ctrl+K" /=]`

Rend un élément `<kbd>` stylé pour ressembler à une touche de clavier. Un argument
positionnel : la combinaison de touches à afficher.

```markdown
Press \[= kbd "Ctrl+K" /=] to focus search.
Press \[= kbd "Cmd+Shift+R" /=] to hard-reload.
```

Appuyez sur [= kbd "Ctrl+K" /=] pour activer la recherche.
Appuyez sur [= kbd "Cmd+Shift+R" /=] pour un rechargement forcé.

### `\[= badge "alpha" /=]`

Une petite étiquette en forme de pilule. Un argument positionnel : le texte intérieur.

```markdown
Tag your alpha pages with \[= badge "alpha" /=] so readers know.
\[= badge "v0.2" /=] \[= badge "beta" /=] \[= badge "experimental" /=]
```

Marquez vos pages alpha avec [= badge "alpha" /=] pour que les lecteurs le sachent.
[= badge "v0.2" /=] [= badge "beta" /=] [= badge "experimental" /=]

## Boutons et appels à l'action

### `\[= button "Label" href="..." /=]` / `\[= buttons =]`

Un lien stylé qui ressemble à un bouton. Le premier argument positionnel est le
libellé ; `href` est l'URL ; définir `primary="true"` bascule vers la variante
primaire pleine. Enveloppez-en un ou plusieurs dans `\[= buttons =]` pour un groupe en ligne.

```markdown
\[= buttons =]
\[= button "Get started" href="/getting-started/" primary="true" /=]
\[= button "Browse on GitHub" href="https://github.com/edadma/juicer" /=]
\[= /buttons =]
```

[= buttons =]
[= button "Commencer" href="/fr/getting-started/" primary="true" /=]
[= button "Parcourir sur GitHub" href="https://github.com/edadma/juicer" /=]
[= /buttons =]

## Médias

### `\[= figure "/img/foo.png" alt="..." caption="..." /=]`

Une `<figure>` avec une image et une légende optionnelle. Le premier argument
positionnel est l'URL de l'image. Arguments nommés : `alt` (texte d'accessibilité),
`caption` (légende visible sous l'image).

```markdown
\[= figure "/img/diagram.svg" alt="Build pipeline diagram" caption="The juicer build pipeline" /=]
```

### `\[= github "owner/repo" /=]`

Une carte cliquable de dépôt GitHub. Un argument positionnel : le radical
`owner/repo`. Rend l'icône octocat de GitHub plus le radical et le sous-titre
« GitHub repository », lié au dépôt.

```markdown
\[= github "edadma/juicer" /=]
```

[= github "edadma/juicer" /=]

### `\[= youtube "<video-id>" /=]`

Une intégration iframe respectueuse de la vie privée (`youtube-nocookie.com`). Un
argument positionnel : l'ID de la vidéo YouTube (la partie après `?v=` dans l'URL).

```markdown
\[= youtube "dQw4w9WgXcQ" /=]
```

## Table de référence

Pour consultation rapide :

| Shortcode      | Paire / auto | Args                                       | À utiliser pour |
|----------------|--------------|--------------------------------------------|-----------------|
| `note`         | paire        | corps                                      | Aparté, info non critique |
| `tip`          | paire        | corps                                      | Suggestion, raccourci |
| `warning`      | paire        | corps                                      | Ralentir, piège |
| `danger`       | paire        | corps                                      | Mise en garde destructrice / sérieuse |
| `steps`        | paire        | corps avec titres `## name`                | Étapes numérotées |
| `tabs`         | paire        | un ou plusieurs enfants `tab`              | Groupes de contenu à onglets |
| `tab`          | paire        | `args[0]` = libellé                        | Un onglet dans `tabs` |
| `filetree`     | paire        | corps opaque                               | Listings en arbre |
| `collapse`     | paire        | `args[0]` = texte de résumé, corps = contenu | Accordéon `<details>` |
| `kbd`          | auto-fermant | `args[0]` = texte de touche                | Style de touche de clavier |
| `badge`        | auto-fermant | `args[0]` = texte de libellé               | Pilule / étiquette |
| `button`       | auto-fermant | `args[0]` = libellé, `href`, `primary`, `icon` | Lien stylé |
| `buttons`      | paire        | un ou plusieurs enfants `button`           | Groupe de boutons |
| `figure`       | auto-fermant | `args[0]` = src, `alt`, `caption`          | Image avec légende |
| `github`       | auto-fermant | `args[0]` = `owner/repo`                   | Carte de dépôt GitHub |
| `youtube`      | auto-fermant | `args[0]` = ID de vidéo                    | Intégration respectueuse de la vie privée |

## Rédiger les vôtres

Déposez un gabarit à `<src>/shortcodes/<name>.html` (ou, dans un thème, à
`<theme>/shortcodes/<name>.html`). À l'intérieur, vous avez accès à :

| Variable    | Quoi |
|-------------|------|
| `.args`     | Arguments positionnels — une liste de chaînes |
| `.<name>`   | Chaque argument nommé par sa clé |
| `.content`  | Le corps, si le shortcode est invoqué en paire (sinon absent) |

Un encadré minimal — gabarit à `<src>/shortcodes/callout.html` :

```squiggly
<aside class="callout callout-{{ if .kind }}{{ .kind }}{{ else }}note{{ end }}">
  {{ markdownify .content }}
</aside>
```

Invoquez-le depuis le markdown :

```markdown
\[= callout kind="success" =]
The build passed.
\[= /callout =]
```

Voir [Concepts → Shortcodes](/fr/concepts/shortcodes/) pour la syntaxe complète
(règles de mise entre guillemets, échappement, paire vs auto-fermant).

## Shortcodes différés — le délimiteur `\[~ … ~]`

La syntaxe classique `\[= … =]` s'exécute **avant** l'analyse du markdown. C'est la
bonne phase pour les shortcodes qui émettent du markdown — un encadré `note`
produisant `<aside>…</aside>` traverse correctement l'analyseur markdown parce que
l'analyseur voit du HTML brut dans l'entrée.

Ce que cette phase **ne peut pas** faire, c'est atteindre `.page.pages`,
`.page.subsections`, `.section.*`, ou tout champ de `.site.*` — ces enregistrements
n'existent pas encore pendant la passe markdown. Tenter de rendre un gabarit de
shortcode qui dit `{{ for p <- .page.pages }}` depuis `\[= … =]` produit
silencieusement une liste vide.

Juicer fournit une seconde paire de délimiteurs de shortcode, `\[~ … ~]`, qui
s'exécute **après** que le pipeline page + section + site a terminé. À l'intérieur
d'un shortcode `\[~`, le gabarit voit :

| Espace de noms   | Ce qui est disponible |
|------------------|-----------------------|
| `.page.*`        | Enregistrement de page complet — title, summary, tags, **pages**, **subsections**, permalink, ancestors, prev / next, series, authors, … |
| `.site.*`        | sitedata complet — `.site.posts`, `.site.pages`, `.site.tags`, `.site.authors`, `.site.now`, `.site.data`, … |
| `.args` / `.<key>` / `.content` | Identique à la passe immédiate |

```markdown
\[~ section-list /~]
```

```squiggly
{{ // shortcodes/section-list.html — must be invoked from the deferred
   // pass because it depends on .page.pages and .page.subsections.   }}
{{ if .page.pages }}
  <ul>{{ for p <- .page.pages }}<li><a href="{{ p.url }}">{{ p.title }}</a></li>{{ end }}</ul>
{{ end }}
```

[= warning =]
**Les shortcodes différés émettent du HTML, pas du markdown.** Ils s'exécutent sur
le corps HTML déjà rendu. Si votre gabarit veut rendre une sortie markdown,
passez-la explicitement par `\{\{ markdownify ... \}\}`.
[= /warning =]

[= note =]
**Même registre de gabarits.** Les deux paires de délimiteurs recherchent les
gabarits dans `shortcodes/` ; la différence est purement *quand* le shortcode
s'exécute et *quel contexte il voit*. Un gabarit qui n'utilise ni `.page` ni
`.site` fonctionne à l'identique depuis l'une ou l'autre passe.
[= /note =]
