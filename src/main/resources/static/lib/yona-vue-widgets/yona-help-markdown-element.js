import { C as e, I as t, M as n, N as r, P as i, S as a, _ as o, c as s, d as c, m as l, n as u } from "./runtime-dom.esm-bundler-B8hvI-p3.js";
import { t as d } from "./_plugin-vue_export-helper-B3ysoDQm.js";
//#region src/help-markdown/examples.ts
var f = [
	{
		key: "markdownHeaders",
		navLabel: "Header",
		input: "# This is an H1\n## This is an H2\n### This is an H3",
		outputHtml: "<h1>This is an H1</h1>\n<h2>This is an H2</h2>\n<h3>This is an H3</h3>"
	},
	{
		key: "markdownStyling",
		navLabel: "Text Style",
		input: "*This is an italic*\n**This is an bold**\n~~This is an strike~~",
		outputHtml: "<p><em>This is an italic</em>\n<strong>This is an bold</strong>\n<del>This is an strike</del></p>"
	},
	{
		key: "markdownLinks",
		navLabel: "Link",
		input: "[Site](http://yobi.io/ \"Yobi Site\")\n\nhttp://yobi.io/",
		outputHtml: "<p><a href=\"http://yobi.io/\" title=\"Yobi Site\">Site</a></p>\n<p><a href=\"http://yobi.io/\">http://yobi.io/</a></p>"
	},
	{
		key: "markdownLists",
		navLabel: "List",
		input: "- Red\n    1. White\n    2. Blue\n- Green.",
		outputHtml: "<ul>\n<li>Red\n<ol>\n<li>White</li>\n<li>Blue</li>\n</ol>\n</li>\n<li>Green.</li>\n</ul>"
	},
	{
		key: "markdownTaskList",
		navLabel: "Checklist",
		input: "- [ ] Todos\n    - [x] To do A\n    - [ ] To do B\n    - [ ] To do C",
		outputHtml: "<ul>\n    <li>\n        <input type=\"checkbox\"> Todos\n        <ul>\n            <li><input type=\"checkbox\" checked> To do A</li>\n            <li><input type=\"checkbox\"> To do B</li>\n            <li><input type=\"checkbox\"> To do C</li>\n        </ul>\n    </li>\n</ul>"
	},
	{
		key: "markdownImages",
		navLabel: "Image",
		input: "![title](https://repo.yona.io/assets/images/ico-like-small.png \"Yobi\")",
		outputHtml: "<p><img src=\"/assets/images/ico-like-small.png\" alt=\"title\" title=\"Yobi\"></p>"
	},
	{
		key: "markdownBlockquotes",
		navLabel: "Blockquote",
		input: "> Lorem ipsum dolor sit amet, consectetuer adipiscing elit.\n>\n> Aenean commodo ligula eget dolor.",
		outputHtml: "<blockquote>\n<p>Lorem ipsum dolor sit amet, consectetuer adipiscing elit.</p>\n<p>Aenean commodo ligula eget dolor.</p>\n</blockquote>"
	},
	{
		key: "markdownCodes",
		navLabel: "Code",
		input: "`function test() {console.log(\"hello world\");}`\n\n```javascript\nfunction test() {\n  console.log(\"hello world\");\n}\n```",
		outputHtml: "<p><code>function test() {console.log(&quot;hello world&quot;);}</code></p>\n<pre><code class=\"javascript\">function test() {\n  console.log(&quot;hello world&quot;);\n}\n</code></pre>"
	},
	{
		key: "markdownTables",
		navLabel: "Table",
		input: "| Default      | Align center | Align right |\n| ------------ | :----------: | ------: |\n| Carrot       | Red          | 1,000   |\n| Banana       | Yellow       | 32,000  |",
		outputHtml: "<table>\n<thead>\n<tr><th>Default</th><th align=\"center\">Align center</th><th align=\"right\">Align right</th></tr>\n</thead>\n<tbody>\n<tr><td>Carrot</td><td align=\"center\">Red</td><td align=\"right\">1,000</td></tr>\n<tr><td>Banana</td><td align=\"center\">Yellow</td><td align=\"right\">32,000</td></tr>\n</tbody>\n</table>\n<p>Also, you can copy &amp; paste table from excel sheet</p>"
	},
	{
		key: "markdownShortLinks",
		navLabel: "Short Link",
		input: "Issue no: #2\nMention: @yobi\ncommit: @763575 or @763575f177a4ce8b9370954de3ea1a1410205593",
		outputHtml: "<p>Issue no: <a href=\"http://demo.yobi.io/yobi/yobi/issue/2\">#2</a></p>\n<p>Mention: <a href=\"http://demo.yobi.io/yobi\">@yobi</a></p>\n<p>commit: <a href=\"http://demo.yobi.io/yobi/yobi/commit/763575\">@763575</a>  or\n<a href=\"http://demo.yobi.io/yobi/yobi/commit/763575f177a4ce8b9370954de3ea1a1410205593\">@763575</a></p>"
	}
];
//#endregion
//#region src/help-markdown/toggle.ts
function p(e, t) {
	return e === t ? null : t;
}
//#endregion
//#region src/help-markdown/MarkdownHelp.vue?vue&type=script&setup=true&lang.ts
var m = { class: "markdown-help" }, h = { class: "markdown-help-nav" }, g = { class: "label" }, _ = ["onClick"], v = { class: "markdown-help-wrap" }, y = { class: "syntax-wrap" }, b = { class: "col syntax" }, x = { class: "col" }, S = ["innerHTML"], C = /*#__PURE__*/ d(/* @__PURE__ */ o({
	__name: "MarkdownHelp",
	props: { title: {
		default: "마크다운 도움말",
		type: String
	} },
	setup(o) {
		let u = n(null);
		function d(e) {
			u.value = p(u.value, e);
		}
		return (n, p) => (a(), l("div", m, [c("ul", h, [c("li", null, [c("span", g, t(o.title), 1)]), (a(!0), l(s, null, e(r(f), (e) => (a(), l("li", {
			key: e.key,
			class: i(["help-nav", { active: u.value === e.key }]),
			onClick: (t) => d(e.key)
		}, t(e.navLabel), 11, _))), 128))]), c("ul", v, [(a(!0), l(s, null, e(r(f), (e) => (a(), l("li", {
			key: e.key,
			class: i(["markdown-help-item", [e.key, { active: u.value === e.key }]])
		}, [p[0] ||= c("div", { class: "thead" }, [c("div", { class: "col" }, "Markdown Input"), c("div", { class: "col" }, "Markdown Output")], -1), c("div", y, [c("div", b, [c("pre", null, t(e.input), 1)]), c("div", x, [c("div", {
			class: "markdown-wrap",
			innerHTML: e.outputHtml
		}, null, 8, S)])])], 2))), 128))])]));
	}
}), [["styles", [".markdown-help[data-v-8751b348]{margin:5px 0 0}.markdown-help-nav[data-v-8751b348]{background-color:#f7f7f7;border:1px solid #ddd;border-bottom:none;margin:0;padding:0;list-style:none}.markdown-help-nav li[data-v-8751b348]{padding:5px 7px;line-height:20px;display:inline-block}.markdown-help-nav li .label[data-v-8751b348]{text-shadow:none;background-color:#c7c9c9}.markdown-help-nav li.help-nav[data-v-8751b348]{cursor:pointer;color:#9e9e9e;position:relative}.markdown-help-nav li.help-nav[data-v-8751b348]:hover{color:#333}.markdown-help-nav li.help-nav.active[data-v-8751b348]{color:#333;font-weight:700}.markdown-help-nav li.help-nav.active[data-v-8751b348]:before{content:\" \";border:7px outset #0000;border-bottom:7px solid #ddd;width:0;height:0;margin-left:-7px;position:absolute;bottom:0;left:50%;overflow:hidden}.markdown-help-nav li.help-nav.active[data-v-8751b348]:after{content:\" \";border:7px outset #0000;border-bottom:7px solid #fff;width:0;height:0;margin-left:-7px;position:absolute;bottom:-1px;left:50%;overflow:hidden}.markdown-help-wrap[data-v-8751b348]{background-color:#fff;margin:0;padding:0;list-style:none}.markdown-help-item[data-v-8751b348]{border-top:none;height:0;overflow:hidden}.markdown-help-item.active[data-v-8751b348]{border:none;border-left:1px solid #ddd;border-right:1px solid #ddd;height:auto;padding:10px}.markdown-help-item .thead[data-v-8751b348]{background-color:#f7f7f7;border:1px solid #ddd;border-bottom:none;border-radius:6px 6px 0 0;display:flex}.markdown-help-item .thead .col[data-v-8751b348]{flex:1 1 0;padding:0 10px;font-weight:700;line-height:30px}.markdown-help-item .syntax-wrap[data-v-8751b348]{border:1px solid #ddd;display:flex}.markdown-help-item .syntax-wrap>.col[data-v-8751b348]{flex:1 1 0;min-width:0}.markdown-help-item .syntax-wrap .syntax[data-v-8751b348]{padding:10px}.markdown-help-item .syntax-wrap .syntax pre[data-v-8751b348]{background-color:#0000;border:none;margin:0;padding:0}.markdown-wrap[data-v-8751b348]{clear:both;-webkit-font-smoothing:antialiased;-webkit-text-size-adjust:100%;font-feature-settings:\"kern\" 1;font-kerning:normal;word-wrap:break-word;font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Helvetica,Arial,sans-serif,Apple Color Emoji,Segoe UI Emoji,Segoe UI Symbol;font-size:1.1em;overflow:auto;padding:15px 20px!important}.markdown-wrap[data-v-8751b348]>:first-child{margin-top:0!important}.markdown-wrap[data-v-8751b348]>:last-child{margin-bottom:0!important}.markdown-wrap[data-v-8751b348] ul,.markdown-wrap[data-v-8751b348] ol{margin-left:0;padding:0 0 5px 2.5em;font-weight:400}.markdown-wrap[data-v-8751b348] li{margin-bottom:5px;line-height:1.6em}.markdown-wrap[data-v-8751b348] li>ul{margin-bottom:0;padding:5px 0 0 2.5em}.markdown-wrap[data-v-8751b348] li>ul :last-of-type{padding-bottom:0}.markdown-wrap[data-v-8751b348] li>ul pre{padding-bottom:10px!important}.markdown-wrap[data-v-8751b348] li>p{margin-top:8px;margin-bottom:2px}.markdown-wrap[data-v-8751b348] a{color:#4183c4;text-decoration:none}.markdown-wrap[data-v-8751b348] a:hover{color:#4183c4;text-decoration:underline}.markdown-wrap[data-v-8751b348] a:hover span{text-decoration:none}.markdown-wrap[data-v-8751b348] a:active{color:#4183c4;text-decoration:none}.markdown-wrap[data-v-8751b348] h1,.markdown-wrap[data-v-8751b348] h2,.markdown-wrap[data-v-8751b348] h3{margin-bottom:16px;line-height:40px}.markdown-wrap[data-v-8751b348] h1{border-bottom:1px solid #eee;width:95%;padding-bottom:.3em;font-size:2em;font-weight:600}.markdown-wrap[data-v-8751b348] h1 .head-anchor,.markdown-wrap[data-v-8751b348] h2 .head-anchor,.markdown-wrap[data-v-8751b348] h3 .head-anchor,.markdown-wrap[data-v-8751b348] h4 .head-anchor,.markdown-wrap[data-v-8751b348] h5 .head-anchor{opacity:0;margin-left:3px}.markdown-wrap[data-v-8751b348] h1:hover .head-anchor,.markdown-wrap[data-v-8751b348] h2:hover .head-anchor,.markdown-wrap[data-v-8751b348] h3:hover .head-anchor,.markdown-wrap[data-v-8751b348] h4:hover .head-anchor,.markdown-wrap[data-v-8751b348] h5:hover .head-anchor{opacity:1}.markdown-wrap[data-v-8751b348] h2{border-bottom:1px solid #eaecef;width:95%;padding:0 0 .3em;font-size:1.5em;line-height:1.25}.markdown-wrap[data-v-8751b348] h3{margin:1em 0 5px;padding:0;font-size:1.25em}.markdown-wrap[data-v-8751b348] h4{margin-top:1.2em;padding:0;font-size:1.25em}.markdown-wrap[data-v-8751b348] h5{margin-top:20px;font-size:1em}.markdown-wrap[data-v-8751b348] hr{color:#ccc;background-color:#ccc;border:0;height:1px;margin:10px 0}.markdown-wrap[data-v-8751b348] p{margin:0 0 12px;line-height:1.6em}.markdown-wrap[data-v-8751b348] blockquote p{font-size:.9em;font-weight:400}.markdown-wrap[data-v-8751b348] code{border:1px solid #ddd;border-radius:3px;padding:5px 5px 2px;font-family:Consolas,Menlo,Monaco,Ubuntu Mono,source-code-pro,monospace;font-size:13px}.markdown-wrap[data-v-8751b348] code .title{font-size:inherit}.markdown-wrap[data-v-8751b348] blockquote{color:#777;border-left:4px solid #ddd;padding:0 15px}.markdown-wrap[data-v-8751b348] li>img{max-width:80%}.markdown-wrap[data-v-8751b348] p>input[type=checkbox]{vertical-align:text-top}.markdown-wrap[data-v-8751b348] li>input[type=checkbox]{vertical-align:top}.markdown-wrap[data-v-8751b348] img{box-sizing:border-box;border:1px solid #0000001a;max-width:100%;max-height:600px;margin:10px 0;padding:5px}.markdown-wrap[data-v-8751b348]>ul{margin-bottom:16px;line-height:20px;list-style:outside}.markdown-wrap[data-v-8751b348] ul ul,.markdown-wrap[data-v-8751b348] ol ul{list-style:circle}.markdown-wrap[data-v-8751b348] ul ul ul,.markdown-wrap[data-v-8751b348] ol ul ul,.markdown-wrap[data-v-8751b348] ol ol ul,.markdown-wrap[data-v-8751b348] ul ol ul{list-style:square}.markdown-wrap[data-v-8751b348] ol{line-height:1.6em;list-style:decimal}.markdown-wrap[data-v-8751b348] pre{word-break:normal;background-color:#efefef;border:none;margin:10px 0;padding:10px;font-size:1em}.markdown-wrap[data-v-8751b348] pre code{border:none;margin:0;padding:0}.markdown-wrap[data-v-8751b348] table{border-collapse:collapse;margin:15px}.markdown-wrap[data-v-8751b348] table th{background-color:#f7f7f7;border:1px solid #dcddde;min-width:45px;padding:5px}.markdown-wrap[data-v-8751b348] table td{word-break:break-all;border:1px solid #dcddde;padding:5px}"]], ["__scopeId", "data-v-8751b348"]]);
//#endregion
//#region src/help-markdown/element.ts
customElements.define("yona-help-markdown", u(C));
//#endregion
