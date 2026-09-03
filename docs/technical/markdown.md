This document describes how markdown support works.

Ported from legacy Yona's `docs/technical/markdown.md`, adapted for yona — the client-side
`markdown` attribute convention is unchanged, but the server-side rendering mechanism is
completely different (no more Play template helper). See
[`docs/guide/technical/markdown.md`](../guide/technical/markdown.md) for the Korean version with
the same content.

Usage
-----

Set the `markdown` attribute on the HTML element to be used as an editor:

    <textarea markdown></textarea>

or to be rendered:

    <div markdown>...</div>

`yobi.Markdown.js` (same file name and location as legacy, `static/javascripts/common/`) finds
elements matching this attribute and handles them.

- Markdown-to-HTML rendering itself happens **client-side**, via `marked.js`
  (`_renderMarkdown()` calls `marked(sText, ...)` then sanitizes with `$yobi.xssClean()`).
- Auto-link resolution (issue/user mentions etc.) needs server-side domain knowledge, so it's
  handled by a separate AJAX POST to `MarkdownController`'s
  `POST /markdown/{owner}/{projectName}` endpoint, with a JSON body
  `{"body": "...", "breaks": true|false}`.

Server-side, the actual markdown engine is **CommonMark Java**
(`org.commonmark:commonmark` + GFM tables/strikethrough/autolink extensions) — a full
replacement for legacy's Nashorn/Rhino JS-engine-based renderer (`lib/js-engine.jar`).
Sanitization uses the **OWASP Java HTML Sanitizer** with an equivalent allowlist policy to
legacy's custom `Markdown.java` (`docs/PARITY_BACKLOG.md` P0-08).
