package utils

import play.mvc.Call
import org.joda.time.DateTimeConstants
import play.i18n.Messages
import controllers.routes
import java.security.MessageDigest
import views.html._
import java.net.URI
import playRepository.DiffLine
import playRepository.DiffLineType
import models.CodeComment
import models.CodeComment.Side
import scala.collection.JavaConversions._
import org.apache.commons.lang3.StringEscapeUtils.escapeHtml4
import views.html.partial_diff_comment_on_line
import views.html.partial_diff_line
import models.PullRequestComment
import models.TimelineItem
import models.Project
import java.net.URLEncoder

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

  def plural(key: String, count: Number): String = {
    var _key = key
    if (count != 1) _key = key + "s"
    Messages.get(_key, count.toString)
  }

  def getJSLink(name: String): String = {
    loadAssetsLink("javascripts", name, "js")
  }

  def getCSSLink(name: String): String = {
   loadAssetsLink("stylesheets", name, "css")
  }

  def loadAssetsLink(base: String, name: String, _type: String): String = {
    var minified = ""
//    if (play.Play.isProd) minified = ".min"
    routes.Assets.at(base + "/" + name + minified + "." + _type).toString
  }

  def urlToPicture(email: String, size: Int = 34) = {
    GravatarUtil.getAvatar(email, size)
  }

  def simpleForm(elements: helper.FieldElements) = {
    elements.input
  }

  def getJSPath(): String = {
    routes.Assets.at("javascripts/").toString
  }

  def nullOrEquals(a: String, b: String) = (a == null || b == null) ||  a.equals(b)

  def equals(a: String, b: String) = (a == b) || a.equals(b)

  // Whether the given uris are pointing the same resource.
  def resourceEquals(a: URI, b: URI) =
    nullOrEquals(a.getAuthority, b.getAuthority) && equals(a.getPath, b.getPath)

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

  def branchItemType(branch: String) = {
    var names = branch.split('/');

    if(names(0).equals("refs") && names.length >= 3){
        names(1) match {
            case "heads" => "branch"
            case "tags"  => "tag"
            case _       => names(1)
        }
    } else {
        branch
    }
  }

  def branchItemName(branch: String) = {
    var names = branch.split('/');

    if(names(0).equals("refs") && names.length >= 3){
        names.slice(2, names.length).mkString("/");
    } else {
        branch
    }
  }

  def branchInHTML(branch: String) = {
    var names = branch.split('/');
    var branchType = branchItemType(branch);
    var branchName = branchItemName(branch);

    if(names(0).equals("refs") && names.length >= 3){
        "<span class=\"label " + branchType + "\">" + branchType + "</span>" + branchName
    } else {
        branch
    }
  }

  def getBranchURL(project:Project, branchName:String, viewType:String, path:String) = {
    viewType match {
        case "history" => {
            routes.CodeHistoryApp.history(project.owner, project.name, URLEncoder.encode(branchName, "UTF-8"), null)
        }
        case "code" => {
            routes.CodeApp.codeBrowserWithBranch(project.owner, project.name, URLEncoder.encode(branchName, "UTF-8"), path)
        }
        case _ => {
            "#"
        }
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

    def writeHtmlLine(klass: String, indicator: String, numA: Integer, numB: Integer, html: String, commentsOnLine: List[_ <: CodeComment]) = {
      partial_diff_line_html(klass, indicator, numA, numB, html) + (if(commentsOnLine != null) partial_diff_comment_on_line(commentsOnLine).body else "")
    }

    def renderWordDiff(lineA: DiffLine, lineB: DiffLine, comments: Map[String, List[_ <: CodeComment]]) = {
      val lines = wordDiffLinesInHtml((new DiffMatchPatch()).diffMain(lineA.content, lineB.content).toList)
      writeHtmlLine(lineA.kind.toString.toLowerCase, "-", null, lineA.numA + 1, lines(0), commentsOrEmpty(comments, commentKey(lineA.file.pathA, "remove", lineA.numA + 1))) + writeHtmlLine(lineB.kind.toString.toLowerCase, "+", lineB.numB + 1, null, lines(1), commentsOrEmpty(comments, commentKey(lineB.file.pathB, "add", lineB.numB + 1)))
    }
    */

    /* Not implemented yet */
    def renderWordDiff(lineA: DiffLine, lineB: DiffLine, comments: Map[String, List[CodeComment]]) =
      renderLine(lineA, comments) + renderLine(lineB, comments)

    def renderTwoLines(lineA: DiffLine, lineB: DiffLine, comments: Map[String, List[CodeComment]]) =
      (lineA.kind, lineB.kind) match {
        case (DiffLineType.REMOVE, DiffLineType.ADD) => renderWordDiff(lineA, lineB, comments)
        case _ => renderLine(lineA, comments) + renderLine(lineB, comments)
      }

    def commentKey(path: String, side: Side, lineNum: Integer) =
      path + ":" + side + ":" + lineNum

    def commentsOrEmpty(comments: Map[String, List[CodeComment]], key: String) =
      if (comments != null && comments.contains(key)) comments(key) else Nil

    def commentsOnAddLine(line: DiffLine, comments: Map[String, List[CodeComment]]) =
      commentsOrEmpty(comments, commentKey(line.file.pathB, Side.B, line.numB + 1))

    def commentsOnRemoveLine(line: DiffLine, comments: Map[String, List[CodeComment]]) =
      commentsOrEmpty(comments, commentKey(line.file.pathA, Side.A, line.numA + 1))

    def commentsOnContextLine(line: DiffLine, comments: Map[String, List[CodeComment]]) =
      commentsOrEmpty(comments, commentKey(line.file.pathB, Side.B, line.numB + 1))

    def indicator(line: DiffLine) =
      line.kind match {
        case DiffLineType.ADD => "+"
        case DiffLineType.REMOVE => "-"
        case _ => " "
      }

    def renderLine(line: DiffLine, num: Integer, numA: Integer, numB: Integer, commentsOnLine: List[CodeComment]) =
      partial_diff_line(line.kind.toString.toLowerCase, indicator(line), num, numA, numB, line.content) +
      partial_diff_comment_on_line(commentsOnLine).body.trim

    def renderLine(line: DiffLine, comments: Map[String, List[CodeComment]]): String =
      line.kind match {
        case DiffLineType.ADD =>
          renderLine(line, line.numB + 1, null, line.numB + 1, commentsOnAddLine(line, comments))
        case DiffLineType.REMOVE =>
          renderLine(line, line.numA + 1, line.numA + 1, null, commentsOnRemoveLine(line, comments))
        case _ =>
          renderLine(line, line.numB + 1, line.numA + 1, line.numB + 1, commentsOnContextLine(line, comments))
      }

    def renderLines(lines: List[DiffLine], comments: Map[String, List[CodeComment]]): String =
      lines match {
        case Nil => ""
        case first::Nil => renderLine(first, comments)
        case first::second::tail => renderTwoLines(first, second, comments) + renderLines(tail, comments)
      }

    def threadAndRemains(commentOnCommit: PullRequestComment, comments: List[TimelineItem]): Tuple2[List[PullRequestComment], List[TimelineItem]] = {
      if (comments.isEmpty) {
        (List(commentOnCommit), Nil)
      } else {
        (commentOnCommit, comments.head) match {
          case (a: PullRequestComment, b: PullRequestComment) if a.threadEquals(b) =>
            (List(a) ++ threadAndRemains(b, comments.tail)._1, threadAndRemains(b, comments.tail)._2)
          case _ =>
            (threadAndRemains(commentOnCommit, comments.tail)._1, List(comments.head) ++ threadAndRemains(commentOnCommit, comments.tail)._2)
        }
      }
    }
  }
}
