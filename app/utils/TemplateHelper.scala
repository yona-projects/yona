package utils

import org.apache.commons.lang3.StringUtils
import play.mvc.Call
import org.joda.time.DateTimeConstants
import org.apache.commons.io.FilenameUtils
import play.i18n.Messages
import controllers.routes
import controllers.UserApp
import views.html._
import java.net.URI
import playRepository.DiffLine
import playRepository.DiffLineType
import models.CodeRange.Side
import scala.collection.JavaConversions._
import views.html.partial_diff_comment_on_line
import views.html.partial_diff_line
import views.html.git.partial_pull_request_event
import models.Organization
import models.PullRequestEvent
import models.PullRequest
import models.Project
import models.Issue
import java.net.URLEncoder
import scala.annotation.tailrec
import playRepository.FileDiff
import play.api.i18n.Lang
import models.CodeCommentThread
import models.CommentThread
import play.twirl.api.Html

object TemplateHelper {

  def buildQueryString(call: Call, queryMap: Map[String, String]): String = {
    val baseUrl = call.toString
    var prefix = "?"
    var query = ""
    if ((baseUrl indexOf "?") != -1) {
      prefix = "&"
    }
    queryMap.map {
      v => query += v._1 + "=" + v._2 + "&"
    }
    baseUrl + prefix + query.dropRight(1)
  }

  def buildAttrString(attrMap: java.util.Map[String, String]): String = {
    var attr = ""
    attrMap.map {
      v => attr += v._1 + "=" + v._2 + " "
    }
    attr.dropRight(1)
  }

  def agoString(duration: org.joda.time.Duration) = {
    if (duration != null){
      val sec = duration.getMillis / DateTimeConstants.MILLIS_PER_SECOND

      sec match {
        case x if x >= 86400 => plural("common.time.day", duration.getStandardDays)
        case x if x >= 3600 => plural("common.time.hour", duration.getStandardHours)
        case x if x >= 60 => plural("common.time.minute", duration.getStandardMinutes)
        case x if x > 0 => plural("common.time.second", duration.getStandardSeconds)
        case x if x == null => ""
        case _ => Messages.get("common.time.just")
      }
    } else {
      ""
    }
  }

  def agoOrDateString(date: java.util.Date) = {
    val ago = JodaDateUtil.ago(date)
    if (ago.getStandardDays < 8) {
        agoString(ago)
    } else {
        JodaDateUtil.getDateString(date, "yyyy-MM-dd")
    }
  }

  def plural(key: String, count: Number): String = {
    var _key = key
    if (count != 1) _key = key + "s"
    Messages.get(_key, count.toString)
  }

  def urlToPicture(email: String, size: Int = 34) = {
    GravatarUtil.getAvatar(email, size)
  }

  def simpleForm(elements: helper.FieldElements) = {
    elements.input
  }

  def getJSPath: String = {
    routes.Assets.at("javascripts/").toString
  }

  def nullOrEquals(a: String, b: String) = (a == null || b == null) ||  a.equals(b)

  def ifElse(condition:Boolean, a: String, b: String): String = {
    if(condition){
      a
    } else {
      b
    }
  }

  def equalsThen(a: String, b: String, thenStr: String): String = {
    if(a != null && b != null && StringUtils.equals(a, b)){
      thenStr
    } else {
      ""
    }
  }

  def getPort(uri: URI) = {
    val port = uri.getPort
    port match {
      case -1 => uri.toURL.getDefaultPort
      case _ => port
    }
  }

  // Whether the given uris are pointing the same resource.
  def resourceEquals(a: URI, b: URI) =
    nullOrEquals(a.getHost, b.getHost) && getPort(a) == getPort(b) && StringUtils.equals(a.getPath, b.getPath)

  // Get the url to return to the list page from the view page.
  // Return the referrer if the it is the uri for the list page, an/ return the
  // default uri if not.
  def urlToList(referrer: String, defaultURI: String) = {
    def fullURI(u: String) = Config.createFullURI(u).normalize
    referrer match {
      case (uri: String) if resourceEquals(fullURI(uri), fullURI(defaultURI)) => uri
      case (_) => defaultURI
    }
  }

  def getUserAvatar(user: models.User, avatarSize:String = "small") = {
    user.refresh();
    var userInfoURL = routes.UserApp.userInfo(user.loginId).toString()

    "<a href=\"" + userInfoURL + "\" class=\"usf-group\" data-toggle=\"tooltip\" data-placement=\"top\" title=\"" + user.name + "\"><img src=\"" + user.avatarUrl + "\" class=\"avatar-wrap " + avatarSize + "\"></a>"
  }

  def urlToProjectBG(project: Project) = {
    models.Attachment.findByContainer(project.asResource) match {
      case files if files.size > 0 => routes.AttachmentApp.getFile(files.head.id)
      case _ => routes.Assets.at("images/project_default.png")
    }
  }

  def urlToProjectLogo(project: Project) = {
    models.Attachment.findByContainer(project.asResource) match {
      case files if files.size > 0 => routes.AttachmentApp.getFile(files.head.id)
      case _ => routes.Assets.at("images/project_default_logo.png")
    }
  }

  def hasProjectLogo(project: Project) = {
    models.Attachment.findByContainer(project.asResource) match {
      case files if files.size > 0 => true
      case _ => false
    }
  }

  /**
   * get branch item name
   * @param branch
   * @return
   */
  def branchItemName(branch:String) = {
    Branches.itemName(branch)
  }

  object Branches {
    def itemType(branch: String): String = {
      val names = branch.split('/').toList

      names match {
        case "refs" :: "heads" :: _ => "branch"
        case "refs" :: "tags"  :: _ => "tag"
        case "refs" :: name    :: _ => name
        case _ => branch
      }
    }

    def itemName(branch: String): String = {
      val names = branch.split("/", 3).toList

      names match {
        case "refs" :: branchType :: branchName => branchName(0)
        case _ => branch
      }
    }

    def branchInHTML(branch: String) = {
      val names = branch.split('/')
      val branchType = itemType(branch)
      val branchName = itemName(branch)

      if(names(0).equals("refs") && names.length >= 3){
        "<span class=\"label " + branchType + "\">" + branchType + "</span>" + branchName
      } else {
        branch
      }
    }

    def getURL(viewType:String, project:Project, branchName:String, path:String) = viewType match {
      case "history" =>
        routes.CodeHistoryApp.history(project.owner, project.name, URLEncoder.encode(branchName, "UTF-8"), null)
      case "code" =>
        routes.CodeApp.codeBrowserWithBranch(project.owner, project.name, URLEncoder.encode(branchName, "UTF-8"), path)
      case _ =>
        "#"
    }

    def getItemHTML(viewType:String, project:Project, branch:String, path:String, selectedBranch:String): String = {
      "<li data-value=\"" + branch + "\"" +
        equalsThen(branch, selectedBranch , "data-selected=\"true\"") + ">" +
        "<a href=\"" + getURL(viewType, project, itemName(branch), path) + "\">" +
        branchInHTML(branch) + "</a>" +
        "</li>"
    }
  }

  def urlToCompare(project: Project, compare: String) = {
    val commits = compare.split(PullRequest.DELIMETER)
    routes.CompareApp.compare(project.owner, project.name, commits(0), commits(1)).url
  }

  def getPercent(unit:Double, total:Double) = {
    ((unit / total) * 100).toInt
  }

  def makeIssuesLink(project:Project, param:Map[String,String]) = {
      buildQueryString(
          routes.IssueApp.issues(project.owner, project.name, "open"),
          param
      )
  }

  def urlToOrganizationLogo(organization: Organization) = {
    models.Attachment.findByContainer(organization.asResource) match {
      case files if files.size > 0 => routes.AttachmentApp.getFile(files.head.id)
      case _ => routes.Assets.at("images/group_default.png")
    }
  }

  def hasOrganizationLogo(organization: Organization) = {
    models.Attachment.findByContainer(organization.asResource) match {
      case files if files.size > 0 => true
      case _ => false
    }
  }

  object DiffRenderer {

    def removedWord(word: String) = "<span class='remove'>" + word + "</span>"

    def addedWord(word: String) = "<span class='add'>" + word + "</span>"

    def mergeList(a: List[String], b: List[String]) = {
        a.zip(b).map(v => v._1 + v._2)
    }

    /*
    def wordDiffLinesInHtml(diffList: List[Diff]): List[String] =
      diffList match {
        case Nil => List("", "")
        case head :: tail => mergeList(wordDiffLineInHtml(head), wordDiffLinesInHtml(tail))
      }

    def wordDiffLineInHtml(diff: Diff) =
      diff.operation match {
        case DELETE => List(removedWord(diff.text), "")
        case INSERT => List("", addedWord(diff.text))
        case _ => List(diff.text, diff.text)
      }

    def writeHtmlLine(klass: String, indicator: String, numA: Integer, numB: Integer, html: String, commentsOnLine: List[_ <: CodeCommentThread]) = {
      partial_diff_line_html(klass, indicator, numA, numB, html) + (if(commentsOnLine != null) partial_diff_comment_on_line(commentsOnLine).body else "")
    }

    def renderWordDiff(lineA: DiffLine, lineB: DiffLine, comments: Map[String, List[_ <: CodeCommentThread]]) = {
      val lines = wordDiffLinesInHtml((new DiffMatchPatch()).diffMain(lineA.content, lineB.content).toList)
      writeHtmlLine(lineA.kind.toString.toLowerCase, "-", null, lineA.numA + 1, lines(0), threadsOrEmpty(comments, threadKey(lineA.file.pathA, "remove", lineA.numA + 1))) + writeHtmlLine(lineB.kind.toString.toLowerCase, "+", lineB.numB + 1, null, lines(1), threadsOrEmpty(comments, threadKey(lineB.file.pathB, "add", lineB.numB + 1)))
    }
    */

    /* Not implemented yet */
    def renderWordDiff(lineA: DiffLine, lineB: DiffLine, comments: Map[String, List[CodeCommentThread]], isEndOfLineMissing: DiffLine => Boolean) =
      renderLine(lineA, comments, isEndOfLineMissing) + renderLine(lineB, comments, isEndOfLineMissing)

    def renderTwoLines(lineA: DiffLine, lineB: DiffLine, comments: Map[String, List[CodeCommentThread]], isEndOfLineMissing: DiffLine => Boolean) =
      (lineA.kind, lineB.kind) match {
        case (DiffLineType.REMOVE, DiffLineType.ADD) => renderWordDiff(lineA, lineB, comments, isEndOfLineMissing)
        case _ => renderLine(lineA, comments, isEndOfLineMissing) + renderLine(lineB, comments, isEndOfLineMissing)
      }

    def threadKey(path: String, side: Side, lineNum: Integer) =
      path + ":" + side + ":" + lineNum

    def threadsOrEmpty(threads: Map[String, List[CodeCommentThread]], key: String) =
      if (threads != null && threads.contains(key)) threads(key) else Nil

    def threadsOnAddLine(line: DiffLine, threads: Map[String, List[CodeCommentThread]]) =
      threadsOrEmpty(threads, threadKey(line.file.pathB, Side.B, line.numB + 1))

    def threadsOnRemoveLine(line: DiffLine, threads: Map[String, List[CodeCommentThread]]) =
      threadsOrEmpty(threads, threadKey(line.file.pathA, Side.A, line.numA + 1))

    def threadsOnContextLine(line: DiffLine, threads: Map[String, List[CodeCommentThread]]) =
      threadsOrEmpty(threads, threadKey(line.file.pathB, Side.B, line.numB + 1))

    def indicator(line: DiffLine) =
      line.kind match {
        case DiffLineType.ADD => "+"
        case DiffLineType.REMOVE => "-"
        case _ => " "
      }

    val noNewlineAtEof = "<span style='color: red'>(" + Messages.get("code.eolMissing") + ")</span>"

    def eolMissingChecker(diff: FileDiff)(line: DiffLine) =
      line.kind match {
        case DiffLineType.REMOVE => (line.numA + 1) == diff.a.size && diff.a.isMissingNewlineAtEnd
        case _ => (line.numB + 1) == diff.b.size && diff.b.isMissingNewlineAtEnd
      }

    def renderLine(line: DiffLine, num: Integer, numA: Integer, numB: Integer,
                   threads: List[CodeCommentThread], isEndOfLineMissing: DiffLine => Boolean) =
      partial_diff_line(line.kind.toString.toLowerCase, indicator(line), num, numA, numB, line.content, isEndOfLineMissing(line)) +
      partial_diff_comment_on_line(threads).body.trim

    def renderLine(line: DiffLine, threads: Map[String, List[CodeCommentThread]], isEndOfLineMissing: DiffLine => Boolean): String =
      line.kind match {
        case DiffLineType.ADD =>
          renderLine(line, line.numB + 1, null, line.numB + 1, threadsOnAddLine(line, threads), isEndOfLineMissing)
        case DiffLineType.REMOVE =>
          renderLine(line, line.numA + 1, line.numA + 1, null, threadsOnRemoveLine(line, threads), isEndOfLineMissing)
        case _ =>
          renderLine(line, line.numB + 1, line.numA + 1, line.numB + 1, threadsOnContextLine(line, threads), isEndOfLineMissing)
      }

    @tailrec def _renderLines(progress: String, lines: List[DiffLine], comments: Map[String, List[CodeCommentThread]], isEndOfLineMissing: DiffLine => Boolean): String =
      lines match {
        case Nil => progress
        case first::Nil => progress + renderLine(first, comments, isEndOfLineMissing)
        case first::second::tail => _renderLines(progress + renderTwoLines(first, second, comments, isEndOfLineMissing), tail, comments, isEndOfLineMissing)
      }

    def renderLines(lines: List[DiffLine], comments: Map[String, List[CodeCommentThread]], isEndOfLineMissing: DiffLine => Boolean): String =
      _renderLines("", lines, comments, isEndOfLineMissing)

    def isAuthorComment(commentId: String) = if(commentId == UserApp.currentUser().loginId) "author"

    def shortId(commitId: String) = commitId.substring(0, Math.min(7, commitId.size))

    @tailrec
    def renderNonRangedThreads(threads: List[models.CommentThread], commitId: String, html: play.twirl.api.Html): play.twirl.api.Html =
      threads match {
        case head :: tail =>
          renderNonRangedThreads(
            tail,
            commitId,
            head match {
              case (thread: models.NonRangedCodeCommentThread)
                if commitId == null || commitId == thread.commitId => new Html(List(html, partial_comment_thread(thread)))
              case _ => html
            }
          )
        case _ => html
      }

    @tailrec
    def _renderEventsOnPullRequest(pull: PullRequest, events: List[PullRequestEvent],
                                   html: play.twirl.api.Html): play.twirl.api.Html =
      events match {
        case head :: tail =>
          _renderEventsOnPullRequest(pull, tail,
            new Html(List(html, partial_pull_request_event(pull, head))))
        case _ => html
      }

    def renderEventsOnPullRequest(pull: PullRequest) =
      _renderEventsOnPullRequest(pull, pull.pullRequestEvents.toList, play.twirl.api.Html(""))

    def urlToCommentThread(thread: CommentThread) = {
        urlToContainer(thread) + "#thread-" + thread.id
    }

    def urlToContainer(thread: CommentThread) = {
      // Before access any field in thread.project, thread.pullRequest and
      // thread.pullRequest.project refresh() should be called because lazy
      // loading does not work for direct field access from Scala source files.
      // See http://www.playframework.com/documentation/2.2.x/JavaEbean
      if (thread.isOnPullRequest) {
          thread.pullRequest.refresh()
          thread.pullRequest.toProject.refresh()
          urlToPullRequest(thread, thread.pullRequest, thread.pullRequest.toProject)
      } else {
          thread.project.refresh()
          urlToCommit(thread, thread.project)
      }
    }

    def urlToPullRequest(thread: CommentThread, pullRequest: PullRequest, project: Project) = {
      thread match {
        case (t: CodeCommentThread) if t.isOnAllChangesOfPullRequest =>
          routes.PullRequestApp.specificChange(
              project.owner,
              project.name,
              pullRequest.number,
              t.isOutdated match {
                case true => t.commitId // This link may occur 404 Not Found because the repository does not have the commit matches with the given commitId.
                case false => ""
              })
        case (t: CodeCommentThread) if t.isOnChangesOfPullRequest =>
          routes.PullRequestApp.specificChange(project.owner, project.name, pullRequest.number, t.commitId)
        case (t: models.NonRangedCodeCommentThread) if t.isOnChangesOfPullRequest =>
          routes.PullRequestApp.specificChange(project.owner, project.name, pullRequest.number, t.commitId)
        case (t: CommentThread) =>
          routes.PullRequestApp.pullRequestChanges(project.owner, project.name, pullRequest.number)
        case _ => ""
      }
    }

    def urlToCommit(thread: CommentThread, project: Project) = {
      thread match {
        case (t: models.NonRangedCodeCommentThread) =>
          routes.CodeHistoryApp.show(project.owner, project.name, t.commitId)
        case (t: CodeCommentThread) =>
          routes.CodeHistoryApp.show(project.owner, project.name, t.commitId)
        case _ => ""
      }
    }

    def urlToPostNewComment(thread: CommentThread) = {
      if(thread.isOnPullRequest){
        routes.PullRequestApp.newComment(thread.project.owner, thread.project.name, thread.pullRequest.id, _getCommitId(thread))
      } else {
        routes.CodeHistoryApp.newComment(thread.project.owner, thread.project.name, _getCommitId(thread))
      }
    }

    def _getCommitId(thread: CommentThread) = {
      thread match {
        case (t: CodeCommentThread) =>
          t.commitId
        case (t: models.NonRangedCodeCommentThread) =>
          t.commitId
        case _ => ""
      }
    }

    def getResourceType(thread: CommentThread) = {
      if(thread.isOnPullRequest){
        models.enumeration.ResourceType.REVIEW_COMMENT
      } else {
        models.enumeration.ResourceType.COMMIT_COMMENT
      }
    }
  }

  object CodeBrowser {
    def fieldText(obj:com.fasterxml.jackson.databind.JsonNode, field:String):String = {
      if(obj.get(field) != null){
        obj.get(field).textValue()
      } else {
        ""
      }
    }

    def getDataPath(listPath:String, fileName:String):String = {
      if(listPath == ""){
        fileName
      }else{
        getCorrectedPath(listPath, fileName)
      }
    }

    def getUserLink(userLoginId:String):String = {
      if(userLoginId != null && userLoginId != ""){
        "/" + userLoginId
      } else {
        "javascript:void(); return false;"
      }
    }

    def getAvatar(file:com.fasterxml.jackson.databind.JsonNode):String = {
      val avatarURL = fieldText(file, "avatar")

      if(avatarURL != null){
        "<a href=\"" + getUserLink(fieldText(file, "userLoginId")) + "\" class=\"avatar-wrap smaller\"><img src=\"" + avatarURL + "\"></a>"
      } else {
        ""
      }
    }

    def getFileClass(file:com.fasterxml.jackson.databind.JsonNode):String = {
      if(fieldText(file, "name") == ".."){
        "updir"
      } else {
        fieldText(file, "type") match {
          case "folder" => "dynatree-ico-cf"
          case _ =>        "dynatree-ico-c"
        }
      }
    }

    def getFileDate(file:com.fasterxml.jackson.databind.JsonNode, field:String)(implicit lang:Lang):String = {
      JodaDateUtil.momentFromNow(file.get(field).longValue, lang.language)
    }

    def getFileAgoOrDate(file:com.fasterxml.jackson.databind.JsonNode, field:String) = {
      agoOrDateString(new java.util.Date(file.get(field).longValue))
    }

    def getCorrectedPath(filePath:String, fileName:String):String = {
      if(StringUtils.isNotEmpty(filePath) && (filePath.substring(filePath.length() - 1) == "/")){
        filePath + fileName
      } else {
        filePath + "/" + fileName
      }
    }

    def getFileRev(vcsType:String, file:com.fasterxml.jackson.databind.JsonNode):String = {
      vcsType match {
        case "GIT" => fieldText(file,"commitId")
        case "Subversion" => fieldText(file, "revisionNo")
        case _ => ""
      }
    }
  }

  def countHtml(icon:String, link:String, count: Int, strong:String = "") = {
      Html("""<span class="count-groups item-icon %s">
        <i class="yobicon-%s"></i>
      </span>
      <span class="count-groups item-count">
        <a href="%s">%d</a>
      </span> """.format(strong, icon, link, count))
  }

  def isMarkdownExtension(path: String):Boolean = {
    var ext = FilenameUtils.getExtension(path).toLowerCase()
    var markdownExtenstions = List("markdown", "mdown", "mkdn", "mkd", "md", "mdwn")

    markdownExtenstions.contains(ext)

  }

}
