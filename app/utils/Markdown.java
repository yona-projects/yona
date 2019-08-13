/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package utils;

import models.Project;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

import javax.annotation.Nonnull;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;

public class Markdown {

    private static final String XSS_JS_FILE = "public/javascripts/lib/xss.js";
    private static final String MARKED_JS_FILE = "public/javascripts/lib/marked.js";
    private static final String HIGHLIGHT_JS_FILE = "public/javascripts/lib/highlight/highlight.pack.js";
    private static ScriptEngine engine = buildEngine();
    private static PolicyFactory sanitizerPolicy = Sanitizers.FORMATTING
            .and(Sanitizers.IMAGES)
            .and(Sanitizers.STYLES)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.BLOCKS)
            .and(new HtmlPolicyBuilder()
                    .allowUrlProtocols("http", "https", "mailto", "file")
                    .allowElements("a", "input", "pre", "br", "hr", "iframe", "ol")
                    .allowAttributes("href", "name", "target").onElements("a")
                    .allowAttributes("type", "disabled", "checked").onElements("input")
                    .allowAttributes("start").onElements("ol")
                    .allowAttributes("width", "height", "src", "frameborder", "allow", "allowfullscreen").onElements("iframe")
                    .allowAttributes("class", "id", "style", "width", "height").globally()
                    .toFactory());

    private static ScriptEngine buildEngine() {
        ScriptEngineManager manager = new ScriptEngineManager(null);
        InputStream is = null;
        Reader reader = null;
        ScriptEngine _engine = manager.getEngineByName("JavaScript");

        try {
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(XSS_JS_FILE);
            reader = new InputStreamReader(is, Config.getCharset());
            _engine.eval(reader);

            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(MARKED_JS_FILE);
            reader = new InputStreamReader(is, Config.getCharset());
            _engine.eval(reader);

        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            if(reader != null) {
                try{ reader.close(); } catch (Exception e) { throw new RuntimeException(e); }
            }
            if(is != null) {
                try{ is.close(); } catch (Exception e) { throw new RuntimeException(e); }
            }
        }

        return _engine;
    }

    private static String removeJavascriptInHref(String source) {
        Document doc = Jsoup.parse(source);

        Elements elements = doc.getElementsByAttribute("href");

        for (Element el : elements) {
            String href = el.attr("href").replaceAll("[^\\w:]", "").toLowerCase();

            if (href.startsWith("javascript:")) {
                el.attr("href", "#");
            }
        }

        return doc.body().html();
    }

    private static String checkReferrer(String source) {
        Boolean noReferrer = play.Configuration.root().getBoolean("application.noreferrer", false);

        if (noReferrer) {
            String hostname = Config.getHostname();

            Document doc = Jsoup.parse(source);

            Elements elements = doc.getElementsByAttribute("href");

            for (Element el : elements) {
                String href = el.attr("href");

                try {
                    URI uri = new URI(href);

                    if (uri.getHost() != null && !uri.getHost().startsWith(hostname)) {
                        el.attr("rel", el.attr("rel") + " noreferrer");
                    }
                }  catch (URISyntaxException e) {
                    // Just skip the wrong link.
                }
            }

            return doc.body().html();
        }
        return source;
    }

    public static String sanitize(String source) {
        return  sanitizerPolicy.sanitize(source);
    }

    private static String renderWithHighlight(String source, boolean breaks) {
        int sourceHashCode = source.hashCode();
        byte [] cached = CacheStore.renderedMarkdown.getIfPresent(sourceHashCode);
        if(cached != null){
            return ZipUtil.decompress(cached);
        }
        try {
            Object options = engine.eval("new Object({ "
                    + "    gfm: true, "
                    + "    tables: true, "
                    + "    breaks: true, "
                    + "    headerIds: true, "
                    + "    pedantic: false, "
                    + "    sanitize: false, "
                    + "    smartLists: true "
                    + "}) ");
            String rendered = renderByMarked(source, options);
            rendered = removeJavascriptInHref(rendered);
            rendered = checkReferrer(rendered);
            String sanitized = sanitize(rendered);
            CacheStore.renderedMarkdown.put(sourceHashCode, ZipUtil.compress(sanitized));
            return sanitized;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Renders the source with Marked.
     *
     * @param source
     * @param options
     * @return the rendered result or the source if timeout occurs
     */
    private static String renderByMarked(@Nonnull final String source, final Object options) throws InterruptedException {
        if (source.isEmpty()) {
            return source;
        }

        // Try to render and wait at most 5 seconds.
        final String[] rendered = new String[1];
        @SuppressWarnings("deprecation")
        Thread marked = new Thread() {
            @Override
            public void run() {
                try {
                    rendered[0] = (String) ((Invocable) engine).invokeFunction(
                            "marked", source, options);
                } catch (Exception e) {
                    play.Logger.error("[Markdown] Failed to render: " + source, e);
                }
            }
        };
        marked.start();
        marked.join(5000);

        if (rendered[0] == null) {
            // This is the only way to stop the script engine. Thread.interrupt does not work.
            marked.stop();
            return "<pre>" + StringEscapeUtils.escapeHtml(source) + "</pre>";
        } else {
            return rendered[0];
        }
    }

    public static String render(@Nonnull String source) {
        int sourceHashCode = source.hashCode();
        byte [] cached = CacheStore.renderedMarkdown.getIfPresent(sourceHashCode);
        if(cached != null){
            return ZipUtil.decompress(cached);
        }
        try {
            Object options = engine.eval("new Object({gfm: true, tables: true, breaks: true, " +
                    "pedantic: false, sanitize: false, smartLists: true});");
            String sanitized = sanitize(renderByMarked(source, options));
            CacheStore.renderedMarkdown.put(sourceHashCode, ZipUtil.compress(sanitized));
            return sanitized;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String render(@Nonnull String source, Project project, boolean breaks) {
        AutoLinkRenderer autoLinkRenderer = new AutoLinkRenderer(renderWithHighlight(source, breaks), project);
        return autoLinkRenderer.render(null);
    }

    public static String render(@Nonnull String source, Project project, boolean breaks, String lang) {
        AutoLinkRenderer autoLinkRenderer = new AutoLinkRenderer(renderWithHighlight(source, breaks), project);
        return autoLinkRenderer.render(lang);
    }

    public static String render(@Nonnull String source, Project project) {
        return render(source, project, true);
    }

    public static String render(@Nonnull String source, Project project, String lang) {
        return render(source, project, true, lang);
    }

    public static String renderFileInCodeBrowser(@Nonnull String source, Project project) {
        String imageLinkFilter = replaceImageLinkPath(project, source);
        AutoLinkRenderer autoLinkRenderer = new AutoLinkRenderer(renderWithHighlight(imageLinkFilter, true), project);
        return autoLinkRenderer.render(null);
    }

    public static String renderFileInReadme(@Nonnull String source, Project project) {
        String relativeLinksToCodeBrowserPath = replaceContentsLinkToCodeBrowerPath(project, source);
        AutoLinkRenderer autoLinkRenderer = new AutoLinkRenderer(renderWithHighlight(relativeLinksToCodeBrowserPath, true), project);
        return autoLinkRenderer.render(null);
    }

    private static String replaceImageLinkPath(Project project, String text){
        String root = play.Configuration.root().getString("application.context", "");
        if (StringUtils.isNotEmpty(root)) {
            root = "/" + root;
        }
        final String imageLink = "!\\[(?<text>[^\\]]*)\\]\\(\\/?(?!https\\:|http\\:|ftp\\:|file\\:)(?<link>[^\\)]*)\\)";
        return text.replaceAll(imageLink, "![$1](/" + root + project.owner + "/" + project.name + "/files/" + project.defaultBranch().replaceAll("refs/heads/", "") + "/$2)");
    }

    private static String replaceContentsLinkToCodeBrowerPath(Project project, String text){
        String root = play.Configuration.root().getString("application.context", "");
        if (StringUtils.isNotEmpty(root)) {
            root = "/" + root;
        }
        final String imageLink = "!\\[(?<text>[^\\]]*)\\]\\(\\/?(?!https\\:|http\\:|ftp\\:|file\\:)(?<link>[^\\)]*)\\)";
        final String normalLocalLink = "(?<space>[^!])\\[(?<text>[^\\]]*)\\]\\(\\/?(?!https\\:|http\\:|ftp\\:|file\\:)(?<link>[^\\)]*)\\)";
        String imageFilteredText = text.replaceAll(imageLink, "![$1](/" + root + project.owner + "/" + project.name + "/files/" + project.defaultBranch().replaceAll("refs/heads/", "") + "/$2)");
        return imageFilteredText.replaceAll(normalLocalLink, "$1[$2](/" + root + project.owner + "/" + project.name + "/code/" + project.defaultBranch().replaceAll("refs/heads/", "") + "/$3)");

    }

}
