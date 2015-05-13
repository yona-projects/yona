/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @author Changgun Kim
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

import controllers.UserApp;
import models.Issue;
import models.Organization;
import models.Project;
import models.User;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.tmatesoft.svn.core.SVNException;
import playRepository.Commit;
import playRepository.PlayRepository;
import playRepository.RepositoryService;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>A renderer that makes auto-links from certain references extracted by HTML rendered by marked.js, using pre-defined patterns.</p>
 *
 * <p>This renderer requires contents of specific objects(issues, comments, etc), and a project containing it.</p>
 *
 * <p>There are examples of how certain references are changed.</p>
 * <pre>
 * User/Project#Num: {@code <a href="The link to specific issue in specific project" class="toIssueLink">User/Project#Num</a>}
 * User#Num: {@code <a href="The link to specific issue in user's same named project" class="toIssueLink">User#Num</a>}
 * #Num: {@code <a href="The link to specific issue in this project" class="toIssueLink">#Num</a>}
 * User/Project@SHA: {@code <a href="The link to specific commit in specific project">User/Project@The short id of this commit</a>}
 * User{@literal @}SHA: {@code <a href="The link to specific commit in user's same named project">User@The short id of this commit</a>}
 * {@literal @}SHA: {@code <a href="The link to specific commit in this project">The short id of this commit</a>}
 * {@literal @}User: {@code <a href="The link to specific user">@User</a>}
 * {@literal @}User/Project: {@code <a href="The link to specific project">@User/Project</a>}
 * </pre>
 */
public class AutoLinkRenderer {
    private static final String PATH_PATTERN_STR = "[a-zA-Z0-9-_./]+";
    private static final String ISSUE_PATTERN_STR = "\\d+";
    private static final String SHA_PATTERN_STR = "[a-f0-9]{7,40}";

    private static final Pattern PATH_WITH_ISSUE_PATTERN = Pattern.compile("@?(" + PATH_PATTERN_STR + ")#(" + ISSUE_PATTERN_STR + ")");
    private static final Pattern ISSUE_PATTERN = Pattern.compile("#(" + ISSUE_PATTERN_STR + ")");

    private static final Pattern PATH_WITH_SHA_PATTERN = Pattern.compile("(" + PATH_PATTERN_STR + ")@?(" + SHA_PATTERN_STR + ")");
    private static final Pattern SHA_PATTERN = Pattern.compile("@?(" + SHA_PATTERN_STR + ")");

    private static final Pattern LOGIN_ID_PATTERN_ALLOW_FORWARD_SLASH_PATTERN = Pattern.compile("@(" + PATH_PATTERN_STR + ")");

    private static final String[] IGNORE_TAGNAME = {"CODE", "A"};

    private static final Pattern WORD_PATTERN = Pattern.compile("\\w");

    private static class Link {
        private static final String DEFAULT_LINK_FORMAT = "<a href='%s' class='%s'>%s</a>";
        public static final Link EMPTY_LINK = new Link();

        public String href;
        public String className;
        public String displayName;

        private Link() {}

        public Link(String href, String displayName) {
            this.href = href;
            this.displayName = displayName;
        }

        public Link(String href, String className, String displayName) {
            this.href = href;
            this.className = className;
            this.displayName = displayName;
        }

        public String toString() {
            return String.format(DEFAULT_LINK_FORMAT,
                    StringUtils.defaultIfEmpty(href, StringUtils.EMPTY),
                    StringUtils.defaultIfEmpty(className, StringUtils.EMPTY),
                    StringUtils.defaultIfEmpty(displayName, StringUtils.EMPTY)
            );
        }

        public boolean isValid() {
            return this != EMPTY_LINK;
        }
    }

    private static interface ToLink {
        public Link toLink(Matcher matcher);
    }

    public String body;
    public Project project;

    public AutoLinkRenderer(String body, Project project) {
        this.body = body;
        this.project = project;
    }

    public String render() {
        return this
                .parse(PATH_WITH_ISSUE_PATTERN, new ToLink() {
                    @Override
                    public Link toLink(Matcher matcher) {
                        String path = matcher.group(1);
                        String issueNumber = matcher.group(2);

                        Project project = getProjectFromPath(path);
                        return toValidIssueLink(path, project, issueNumber);
                    }
                })
                .parse(ISSUE_PATTERN, new ToLink() {
                    @Override
                    public Link toLink(Matcher matcher) {
                        return toValidIssueLink(project, matcher.group(1));
                    }
                })
                .parse(PATH_WITH_SHA_PATTERN, new ToLink() {
                    @Override
                    public Link toLink(Matcher matcher) {
                        String path = matcher.group(1);
                        String SHA = matcher.group(2);

                        Project project = getProjectFromPath(path);
                        return toValidSHALink(path, project, SHA);
                    }
                })
                .parse(SHA_PATTERN, new ToLink() {
                    @Override
                    public Link toLink(Matcher matcher) {
                        return toValidSHALink(project, matcher.group(1));
                    }
                })
                .parse(LOGIN_ID_PATTERN_ALLOW_FORWARD_SLASH_PATTERN, new ToLink() {
                    @Override
                    public Link toLink(Matcher matcher) {
                        String path = matcher.group(1);

                        int slashIndex = path.indexOf("/");

                        if (slashIndex > -1) {
                            return toValidProjectLink(path.substring(0, slashIndex), path.substring(slashIndex + 1));
                        } else {
                            return toValidUserLink(path);
                        }
                    }
                })

                .body;
    }

    private AutoLinkRenderer parse(Pattern pattern, ToLink toLink) {
        Document doc = Jsoup.parse(body);

        Document.OutputSettings settings = doc.outputSettings();
        settings.prettyPrint(false);

        Elements elements = doc.getElementsMatchingOwnText(pattern);

        for (Element el : elements) {
            if (isIgnoreElement(el)) {
                continue;
            }

            List<TextNode> textNodeList = el.textNodes();

            for (TextNode node : textNodeList) {
                String result = convertLink(node.text(), pattern, toLink);
                node.text(StringUtils.EMPTY);
                node.after(result);
            }
        }

        this.body = doc.body().html();
        return this;
    }

    /**
     * Using patterns, certain reference into auto-link, using pattern
     *
     * @param pattern
     * @param toLink
     * @return
     */
    private String convertLink(String text, Pattern pattern, ToLink toLink) {
        Matcher matcher = pattern.matcher(text);

        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            if (isWrappedNonCharacter(text, matcher)) {
                continue;
            }
            Link link = toLink.toLink(matcher);

            if (link.isValid()) {
                matcher.appendReplacement(sb, link.toString());
            }
        }

        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Get a project from a path consisting of owner and project's name
     *
     * @param path
     * @return
     */
    private Project getProjectFromPath(String path) {
        int slashIndex = path.indexOf("/");

        /**
         * If owner has same named project, the project name can be skipped
         * See https://help.github.com/articles/writing-on-github/#references
         */
        if (slashIndex > -1) {
            return Project.findByOwnerAndProjectName(path.substring(0, slashIndex), path.substring(slashIndex + 1));
        } else {

            return Project.findByOwnerAndProjectName(path, project.name);
        }
    }

    private Link toValidIssueLink(Project project, String issueNumber) {
        return toValidIssueLink(StringUtils.EMPTY, project, issueNumber);
    }

    private Link toValidIssueLink(String prefix, Project project, String issueNumber) {
        if (project != null) {
            Issue issue = Issue.findByNumber(project, Long.parseLong(issueNumber));

            if (issue != null) {
                /**
                 * CSS class name of a link to specific issue is 'issueLink'.
                 * CSS class name can enable to show the quick view of issue.
                 */
                if (StringUtils.isEmpty(prefix)) {
                    return new Link(RouteUtil.getUrl(issue), "issueLink", "#" + issueNumber);
                } else {
                    return new Link(RouteUtil.getUrl(issue), "issueLink", prefix + "#" + issueNumber);
                }
            }
        }

        return Link.EMPTY_LINK;
    }

    private Link toValidSHALink(Project project, String SHA) {
        return toValidSHALink(StringUtils.EMPTY, project, SHA);
    }

    private Link toValidSHALink(String prefix, Project project, String sha) {
        if (project != null) {
            try {
                if (!project.isCodeAvailable() || !project.isGit()) {
                    return Link.EMPTY_LINK;
                }

                PlayRepository repository = RepositoryService.getRepository(project);

                if (repository != null) {
                    Commit commit = repository.getCommit(sha);

                    if (commit != null) {
                        if (StringUtils.isEmpty(prefix)) {
                            return new Link(RouteUtil.getUrl(commit, project), commit.getShortId());
                        } else {
                            return new Link(RouteUtil.getUrl(commit, project), prefix + "@" + commit.getShortId());
                        }
                    }
                }
            } catch (SVNException svnException) {
                return Link.EMPTY_LINK;
            } catch (IOException ioException) {
                return Link.EMPTY_LINK;
            } catch (ServletException servletException) {
                return Link.EMPTY_LINK;
            }
        }

        return Link.EMPTY_LINK;
    }

    private static Link toValidUserLink(String userId) {
        User user = User.findByLoginId(userId);
        Organization org = Organization.findByName(userId);

        if(org != null) {
            return new Link(RouteUtil.getUrl(org), "@" + org.name);
        }

        if (user.isAnonymous() ) {
            return Link.EMPTY_LINK;
        } else {
            String avatarImage;
            if( user.avatarUrl().equals(UserApp.DEFAULT_AVATAR_URL) ){
                avatarImage = "";
            } else {
                avatarImage = "<img src='" + user.avatarUrl() + "' class='avatar-wrap smaller no-margin-no-padding vertical-top' alt='@" + user.loginId + "'> ";
            }
            return new Link(RouteUtil.getUrl(user), "no-text-decoration", "<span data-toggle='popover' data-placement='top' data-trigger='hover' data-html='true' data-content=\"" + StringEscapeUtils.escapeHtml4(avatarImage + user.name) + "\">@" + user.loginId + "</span>");
        }
    }

    private static Link toValidProjectLink(String ownerName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);

        if (project != null) {
            return new Link(RouteUtil.getUrl(project), "@" + project.toString());
        } else {
            return Link.EMPTY_LINK;
        }
    }

    /**
     * * Check whether element is links, code tags.
     * @param el
     * @return
     */
    private boolean isIgnoreElement(Element el) {
        return ArrayUtils.contains(IGNORE_TAGNAME, el.tagName().toUpperCase());
    }

    /**
     * Check whether a found matcher is wrapped in non-word character
     *
     * @param body
     * @param matcher
     * @return
     */
    private static boolean isWrappedNonCharacter(String body, Matcher matcher) {
        return (matcher.start() != 0 && WORD_PATTERN.matcher(body.substring(matcher.start() - 1, matcher.start())).find()) ||
                (matcher.end() != body.length() && WORD_PATTERN.matcher(body.substring(matcher.end(), matcher.end() + 1)).find());
    }
}
