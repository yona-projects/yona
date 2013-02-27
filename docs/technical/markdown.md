This document describes how to enable markdown support.
* Use a textarea as a markdown editor.
* Render plaintext written in markdown syntax.

Usage
-----

First, Set `markdown` attribute on the HTML element to be used as an editor

    <textarea markdown></textarea>

or be rendered.

    <div markdown>@issue.body</div>

Then call @views.html.markdown in your scala template, to enable the markdown
support.

    @views.html.markdown()
