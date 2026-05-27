---
title: Shortcodes
summary: Des gabarits intégrables au markdown pour les encadrés, onglets, intégrations et plus.
weight: 40
---

Les shortcodes permettent aux auteurs d'invoquer des gabarits HTML depuis l'intérieur du markdown sans écrire de HTML brut. Ils utilisent une syntaxe crochet-égal pour éviter les collisions avec le markdown et avec squiggly :

```markdown
\[= shortcode-name positional "another arg" key="value" =]
optional body content
\[= /shortcode-name =]
```

Le contenu du corps est capturé brut et exposé au gabarit via `.content` ; les arguments positionnels deviennent `.args` (une liste) ; les arguments nommés deviennent `.<name>`.

## Ce que juicerdocs fournit

Ce thème même offre un ensemble utile dès le départ. Quelques exemples :

[= note =]
**Les encadrés de note** comme celui-ci s'écrivent avec `\[= note =] ... \[= /note =]`. Nous avons les variantes `tip`, `warning` et `danger` pour différentes couleurs et icônes.
[= /note =]

[= tip =]
**Astuce :** si vous rédigez beaucoup de documentation structurée, les shortcodes `steps`, `tabs` et `filetree` vous épargnent beaucoup de HTML.
[= /tip =]

[= warning =]
**Attention :** les shortcodes sont traités *avant* l'analyse du markdown, vous ne pouvez donc pas placer un shortcode dans un bloc de code indenté.
[= /warning =]

[= danger =]
**À ne pas faire :** n'exposez jamais directement dans le HTML un shortcode qui prend une entrée utilisateur. Passez-la par `markdownify` ou échappez-la explicitement.
[= /danger =]

## Onglets

La paire `tabs` / `tab` donne du contenu à onglets. Utile pour montrer la même opération sur plusieurs plateformes :

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

## Étapes

Utilisez `\[= steps =]` et écrivez des titres `## Nom de l'étape` à l'intérieur ; ils sont numérotés automatiquement :

[= steps =]
## Installer Juicer

Clonez le dépôt et vérifiez avec `sbt 'juicerJVM/run --help'`.

## Créer un répertoire de site

Créez `site.toml`, un dossier `content/` et `layouts/_default/`.

## Construire et servir

Lancez `sbt 'juicerJVM/run serve -s .'` et ouvrez <http://localhost:8080>.
[= /steps =]

## Arborescences de fichiers

`\[= filetree =]` n'est que de la police à chasse fixe + des guides d'indentation — collez à l'intérieur un listing en arborescence :

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

## Éléments en ligne

Appuyez sur [= kbd "Ctrl+K" /=] pour activer la recherche. Étiquetez vos pages alpha avec [= badge "alpha" /=] pour que les lecteurs le sachent.

## Sections repliables

Idéal pour les FAQ et les « montre-moi la version longue » :

[= collapse "Pourquoi pas de frontmatter JSON ?" =]
Le format du frontmatter est YAML parce que YAML couvre le cas d'usage avec aisance et que prendre en charge trois formats (YAML, TOML, JSON) triple la surface à maintenir correcte. Le JSON dans le markdown est aussi visuellement bruyant.
[= /collapse =]

[= collapse "Et le frontmatter TOML ?" =]
Même réponse. La configuration du site est en TOML ; les métadonnées par page sont en YAML ; cela fait deux formats, mais ils vivent dans des fichiers différents, et la distinction est facile à retenir.
[= /collapse =]

## Boutons

Pour les pages d'atterrissage et les appels à l'action :

[= buttons =]
[= button "Commencer" href="/fr/getting-started/" primary="true" /=]
[= button "Parcourir sur GitHub" href="https://github.com/edadma/juicer" /=]
[= /buttons =]

## Cartes de dépôt GitHub

`\[= github "owner/repo" /=]` rend une carte cliquable :

[= github "edadma/juicer" /=]

## Intégrations YouTube

`\[= youtube "<video-id>" /=]` intègre un iframe respectueux de la vie privée (`youtube-nocookie.com`).

## Écrire le vôtre

Déposez un gabarit dans `<theme>/shortcodes/<name>.html` (ou `<site>/shortcodes/<name>.html`). À l'intérieur, vous avez accès à :

| Variable  | Rôle |
|-----------|------|
| `.args`   | Arguments positionnels — une liste de chaînes |
| `.<name>` | Chaque argument nommé par sa clé |
| `.content`| Le corps, si le shortcode est délimité |

Utilisez la fonction native `markdownify` pour rendre le corps comme du markdown :

```squiggly
<div class="my-callout">
  {{ markdownify .content }}
</div>
```

## Deux passes : immédiate vs différée

Juicer exécute les shortcodes en **deux passes** :

1. **Immédiate** — le délimiteur classique `\[= name =]`. S'exécute *avant*
   l'analyse du markdown, de sorte que la sortie du shortcode traverse
   l'analyseur markdown. Idéal pour les shortcodes dont le corps est du markdown
   (encadrés, onglets, sections repliables, …). Le compromis : cette passe a lieu
   avant que le pipeline page + section + site n'assemble ses données, donc
   le gabarit du shortcode ne voit que `.args`, les clés nommées et
   `.content` — pas de `.page` ni de `.site`.
2. **Différée** — le délimiteur `\[~ name ~]`. S'exécute *après* que le
   pipeline a terminé, sur le corps HTML déjà rendu. Le gabarit du
   shortcode peut atteindre `.page.title`, `.page.pages`,
   `.page.subsections`, `.section.*` et l'intégralité de `.site.*`.
   Compromis : le shortcode émet du HTML directement (l'analyseur markdown
   a déjà tourné), passez donc le markdown par `markdownify` explicitement
   si vous en avez besoin.

```markdown
\[= note =]Immédiate — émet du markdown que l'analyseur rend ensuite.\[= /note =]

\[~ pagebadge /~]
```

```squiggly
{{ // shortcodes/pagebadge.html — accède à .page.title, doit donc être
   //  invoqué depuis la passe différée.                              }}
<span class="badge">{{ .page.title }}</span>
```

Les deux passes recherchent les gabarits dans le même répertoire `shortcodes/` ; le
délimiteur choisit quelle passe exécute le gabarit. Un gabarit qui ne
référence ni `.page` ni `.site` peut être invoqué depuis l'une ou l'autre.
