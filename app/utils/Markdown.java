/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package utils;

import controllers.UserApp;
import models.Issue;
import models.Project;
import models.enumeration.Operation;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import play.i18n.Messages;

import javax.annotation.Nonnull;
import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                    .allowUrlProtocols("http", "https", "mailto", "file", "zpl")
                    .allowElements("video", "source", "a", "input", "pre", "br", "hr", "iframe", "ol", "span")
                    .allowAttributes("href", "name", "target").onElements("a")
                    .allowAttributes("src", "type", "target").onElements("source")
                    .allowAttributes("data-setup", "controls", "preload", "type", "autoplay", "responsive", "height", "width", "fluid", "liveui", "src").onElements("video")
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

    private static String transformIssueLink(String source) {

        String hostname = Config.getHostname();

        Document doc = Jsoup.parse(source);

        Elements elements = doc.getElementsByAttribute("href");

        for (Element el : elements) {
            String href = el.attr("href");
            String linkText = el.text();

            try {
                URI uri = new URI(href);

                if (href.startsWith("/") || uri.getHost() != null && uri.getHost().startsWith(hostname)
                        && StringUtils.equals(linkText, href)) {
                    el.attr("rel", el.attr("rel") + " noreferrer");

                    if (extractIssueLink(el, uri)) break;
                }
            } catch (URISyntaxException e) {
                // Just skip the wrong link.
            }
        }

        return doc.body().html();
    }

    private static boolean extractIssueLink(Element el, URI uri) {
        // issue link 인지 검사
        // 이미 issue link 로 구분되어 있는지 검사 (class 이름이 issueLink 이면 이미 구분된 상태)
        // 일반 issue link url 맞으면 Issue 모델을 찾아냄
        // link text를 이슈 번호와 제목으로 변경
        // 만약 코멘트까지 지정되어 있다면 이슈번호#코멘트id 로 표시

        Pattern pattern = Pattern.compile("/issue/\\d+");
        Matcher matcher = pattern.matcher(uri.getPath());

        if (matcher.find()) {
            String linkText = el.text();

            String[] segments = uri.getPath().split("/issue/");

            try {
                if ( segments.length > 1) {
                    String[] s = segments[0].split("/");
                    String owner = s[s.length - 2];
                    String projectName = s[s.length - 1];
                    long number = Long.parseLong(segments[1]);

                    Project project = Project.findByOwnerAndProjectName(owner, projectName);
                    Issue issue = Issue.findByNumber(project, number);

                    if (!AccessControl.isAllowed(UserApp.currentUser(), issue.asResource(), Operation.READ)){
                        return true;
                    }

                    linkText =  "#" + issue.getNumber() + "." + issue.title;
                    String fragment = uri.getFragment();
                    if (fragment != null) {
                        linkText += "#" + fragment;
                    }

                    el.text("");
                    el.prependText(linkText);
                    el.addClass("issueLink");
                    el.appendElement("span")
                            .addClass("issue-state")
                            .addClass(issue.state.state().toLowerCase())
                            .text(Messages.get("issue.state." + issue.state.state()));
                }
            } catch (RuntimeException re) {
                play.Logger.warn("Issue link extraction fail: " + uri.getPath());
            }


        }
        return false;
    }


    public static String sanitize(String source) {
        return  sanitizerPolicy.sanitize(source);
    }

    private static String renderWithHighlight(String source, boolean breaks) {
        int sourceHashCode = source.hashCode();
        byte [] cached = CacheStore.renderedMarkdown.getIfPresent(sourceHashCode);
        if(cached != null){
            Runnable afterTouch = new Runnable() {
                @Override
                public void run() {
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
                        rendered = transformIssueLink(rendered);
                        String sanitized = sanitize(rendered);
                        CacheStore.renderedMarkdown.put(sourceHashCode, ZipUtil.compress(sanitized));
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }
            };

            afterTouch.run();
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
            rendered = transformIssueLink(rendered);
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
        final String imageLink = "!\\[(?<text>[^]]*)]\\(/?(?!https:|http:|ftp:|file:)[.][/](?<link>.*)\\)";
        return text.replaceAll(imageLink, "![$1](/" + root + project.owner + "/" + project.name + "/files/" + project.defaultBranch().replaceAll("refs/heads/", "") + "/$2)");
    }

    private static String replaceContentsLinkToCodeBrowerPath(Project project, String text){
        String root = play.Configuration.root().getString("application.context", "");
        if (StringUtils.isNotEmpty(root)) {
            root = "/" + root;
        }
        final String imageLink = "!\\[(?<text>[^]]*)]\\(/?(?!https:|http:|ftp:|file:)[.][/](?<link>.*)\\)";
        final String normalLocalLink = "(?<space>[^!])\\[(?<text>[^]]*)]\\(/?(?!https:|http:|ftp:|file:)[.][/](?<link>.*)\\)";
        String imageFilteredText = text.replaceAll(imageLink, "![$1](/" + root + project.owner + "/" + project.name + "/files/" + project.defaultBranch().replaceAll("refs/heads/", "") + "/$2)");
        return imageFilteredText.replaceAll(normalLocalLink, "$1[$2](/" + root + project.owner + "/" + project.name + "/code/" + project.defaultBranch().replaceAll("refs/heads/", "") + "/$3)");

    }

}
