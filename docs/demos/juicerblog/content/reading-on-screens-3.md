---
title: "Reading on screens, part 3: long-form reading patterns"
date: 2024-08-05
author: alice
tags: [design, web, typography]
series: "Reading on screens"
seriesOrder: 3
summary: People scan first and read second. The article that survives that first scan is the one that gets read. Eight design moves that survive the scan.
---

Eye-tracking studies on long-form web reading converge on the same boring conclusion: the first thing your reader does is *scan*. They glance at the title, fall to the first paragraph, jump down to scan headings, scroll the bar to gauge length, scroll back up, and only then commit to reading.

If your article doesn't survive that scan, it doesn't get read. The encouraging news is that "surviving the scan" is mostly a structural problem, not a writing one.

## 1. Front-load the thesis

The first sentence is the only sentence you can guarantee gets read. It should answer: *what is this article about, and why should I keep going?* Burying the lede past a paragraph of throat-clearing is the single most common failure mode of essays that "didn't take off."

Compare:

> I've been thinking about how people read on screens lately, ever since I noticed that…

vs.

> People scan first and read second. The article that survives that first scan is the one that gets read.

The second version costs you the warm-up. It buys you the reader.

## 2. Scannable headings

Your headings should make sense read in isolation. A reader scrolling past should be able to reconstruct the article's argument from the headings alone. "A note on history" is decorative; "Why the Greek alphabet won" is structural.

The test: copy your article's headings into a new document. Do they form a coherent outline?

## 3. The 38-rem column

Comfortable reading hits 60–75 characters per line. At a body font size of 17–18 px, that's 38–42 rem. Wider columns force the eye to track too far horizontally and lose the line break; narrower columns produce too many line breaks per minute and the eye fatigues.

The web's instinct to fill the viewport horizontally is a UI-design instinct, not a reading instinct. Reading wants the column to be small.

## 4. Small inline contrast

Italic and bold are the two pieces of structural emphasis that survive the scan. They mark *this is a definition*, *this is the key word*, *this is the contradiction I'm flagging*.

Use them sparingly. A page with five bolded phrases pulls the eye to all five and the eye trusts none of them. A page with one bolded phrase per scroll-screen makes that phrase the load-bearing claim.

## 5. Lists for parallel structure

Lists are good when items are parallel:

- **Subject parallel.** "Three things designers get wrong about color."
- **Form parallel.** Each item begins with a verb in the same tense.
- **Length parallel.** Items don't vary by 10x in word count.

Lists are bad when forced. If your three "items" are really one big idea broken into three sentences, write the sentences. The reader will follow.

## 6. Code blocks as visual anchors

Even a non-programmer reader uses code blocks as scroll waypoints. The reader scrolls, sees a code block, registers "technical content here," and may or may not stop. Make sure the prose around your code block stands on its own — readers who skip code should still come away with the argument.

This means: no "as you can see in the code above" phrasing. Restate the point in prose immediately after.

## 7. Pull quotes earn their keep

A pull quote that just repeats a sentence from the body is decorative. A pull quote that *isolates a claim* and gives the eye a place to land while scrolling is structural.

> The unspoken floor for "comfortable long-form reading" is closer to 8–13:1 contrast — well above WCAG AA.

That's a pull quote. It commits to a claim, in language that survives being lifted out of context.

## 8. End with a turn

The final paragraph should turn the article into a question, a recommendation, or a stake. Don't summarize what you just said — the reader was there. Move the conversation forward.

This series ran three parts: the substrate (subpixels and hinting), the chemistry (color and dark mode), and the choreography (how the eye moves through long-form). The next move, for any one essay, is yours: open the document and look at it for thirty seconds without reading it. What did your eye do? *That's* what your reader is doing too.
