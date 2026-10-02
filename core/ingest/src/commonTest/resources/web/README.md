# Web extraction fixtures

Saved pages for `WebPageExtractor` and its site extractors (docs/web/ROADMAP.md, ADR 0012). Tests serve
them from MockWebServer; nothing here is read from the network at test time and none of it is shipped
in the app.

## Wikipedia (CC BY-SA 4.0)

Text from Wikipedia is licensed [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/); the
authors are listed in each page's history. Every file is the Parsoid HTML that the REST API returned
on 2026-10-02 (`GET https://<lang>.wikipedia.org/api/rest_v1/page/html/<title>`, HTML spec 2.8.0), then
**trimmed**: top-level sections the tests don't need were removed, long sections cut to their first few
blocks, reference lists cut to a handful of entries, and the `data-mw` attributes (large JSON that
extractors don't read) removed. Everything else is as served, including `<style>` elements, `<link>`
tags, navboxes and the citation markup. Do not "fix" the markup by hand: re-trim a fresh copy instead.

| File | Page | Revision | Last edited | Kept sections | What it exercises |
|---|---|---|---|---|---|
| `amygdala.html` | [Amygdala](https://en.wikipedia.org/wiki/Amygdala) | 1377732379 | 2026-09-30 | lead, Structure (+ subsection), See also, References, Further reading, External links | the plain case: short-description block, hatnote, infobox, thumbnails, `sup.mw-ref` citations, the references list, a navbox inside External links, no math |
| `fourier-transform.html` | [Fourier transform](https://en.wikipedia.org/wiki/Fourier_transform) | 1377137736 | 2026-09-28 | lead, Definition (with its `wikitable`), Alternatives, Example, See also, Notes, Citations, References, External links | math: inline and block `mwe-math-element`, TeX in `<math alttext>` and in the fallback `<img alt>`, always wrapped in `{\displaystyle …}`; a data table; two hatnotes |
| `ja-amygdala.html` | [扁桃体](https://ja.wikipedia.org/wiki/扁桃体) | 110597200 | 2026-08-10 | lead, 解剖学的下位領域 (+ subsections), 関連項目, 出典, 外部リンク | a non-English article: Japanese section titles (end matter named 関連項目 / 出典 / 外部リンク), text without spaces for `WordCount` and the chunker |
| `mercury-disambiguation.html` | [Mercury](https://en.wikipedia.org/wiki/Mercury) (reached from `Mercury_(disambiguation)`) | 1376049828 | 2026-09-21 | lead and the first three sections, the last two | a disambiguation page: `dmbox-disambig` message box, `Category:Disambiguation_pages` links, a page that is only lists of links |
| `wikipedia-redirect.txt` | `Fourier_Transform` | – | – | – | the 307 the API answers for a redirect title; the target is `fourier-transform.html` |

Findings that shape W3 (record them in the ADR if they change):

- `/api/rest_v1/page/html/<title>` answers a redirect with **307** and a *relative* `Location:
  /w/rest.php/v1/page/<Target>/html?redirect=no` on the same host. OkHttp follows it; the final URL
  (not the typed one) names the real article.
- A title in another namespace is still served (`Talk:…` gives 200), `Special:…` and missing pages give
  404. `handles` must refuse the namespace prefixes itself; it can't rely on the API.
- A formula appears **twice**: a hidden `<math alttext>` (MathML) in a `display:none` span, and a
  fallback `<img class="mwe-math-fallback-image-…" alt>` with the same TeX. A converter must emit one.
  The class is `mwe-math-element-inline` or `mwe-math-element-block` on the wrapping span.
- A disambiguation page is recognised by `.dmbox-disambig` or a category link whose `href` ends in
  `Disambiguation_pages`; the link class `mw-disambig` is on *links to* such pages (hatnotes), not on
  the page itself. A hatnote "for other uses, see X (disambiguation)" does **not** make a page one.
- Sections are nested `<section data-mw-section-id>` elements (an `h3` section sits inside its `h2`
  section); the lead is section `0` and has no heading. Section ids are stable within one revision only.
- The infobox is `table.infobox`; the short description is a hidden `div.shortdescription`; citations
  are `sup.mw-ref` and the list `ol.mw-references`; Notes / Citations / References on large pages are
  separate sections, and the long bibliography is a `div.refbegin` with a `ul`.

## Generic pages (written for this repository, public domain)

Hand-written, small, and made to look like what each kind of site sends.

| File | Exercises |
|---|---|
| `news-article.html` | `<article>` with byline, figure caption, list, blockquote; chrome around it (header, nav, cookie banner, aside, footer) |
| `blog-entry-content.html` | no `<article>`: the text is in `.entry-content`; sidebar, share links and comments to leave out; inline code, an ordered list |
| `docs-page.html` | `<main id="main-content">`; a `<pre><code class="language-kotlin">` block with a blank line and indentation, nested lists (`ul` in `ul`, `ol` in `ul`), a table with a header row and a `|` in a cell, inline code containing backticks |
| `div-soup.html` | no semantic markup at all: text in `div`s, chrome in `div`s; read by `ContentFinder`'s scoring (W6), where the old rule fell back to `body` |
| `div-article.html` | W6: no semantic markup, a link list and a "reader note" area (neither under a tag or class the old rule dropped) that together hold more text than a short story; the story is in `#story`, the paragraphs in `div`s |
| `guide-with-sections.html` | W6: `<main>` with an `h1`, three `h2` and an `h3`, and a code block whose `#` lines are not headings: sections from headings |
