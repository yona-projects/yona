/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Keesun Baik
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package utils;

import models.Project;
import org.apache.commons.lang.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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

            is = Thread.currentThread().getContextClassLoader().getResourceAsStream(HIGHLIGHT_JS_FILE);
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

    private static String sanitize(String source) {
        try {
            Object filter = engine.eval("new Filter();");
            return (String) ((Invocable) engine).invokeMethod(filter, "defence", source);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static String renderWithHighlight(String source, boolean breaks) {
        try {
            Object options = engine.eval("new Object({gfm: true, tables: true, breaks: " + breaks + ", " +
                    "pedantic: false, sanitize: false, smartLists: true," +
                    "highlight : function(sCode, sLang) { " +
                    "if(sLang) { try { return hljs.highlight(sLang.toLowerCase(), sCode).value;" +
                    " } catch(oException) { return sCode; } } }});");
            String rendered = renderByMarked(source, options);
            rendered = removeJavascriptInHref(rendered);
            rendered = checkReferrer(rendered);
            return sanitize(rendered);
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
        try {
            Object options = engine.eval("new Object({gfm: true, tables: true, breaks: true, " +
                    "pedantic: false, sanitize: false, smartLists: true});");
            return sanitize(renderByMarked(source, options));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String render(@Nonnull String source, Project project, boolean breaks) {
        AutoLinkRenderer autoLinkRenderer = new AutoLinkRenderer(renderWithHighlight(source, breaks), project);
        return autoLinkRenderer.render();
    }

    public static String render(@Nonnull String source, Project project) {
        return render(source, project, true);
    }
}
