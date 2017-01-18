/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package controllers;

import com.avaje.ebean.ExpressionList;
import com.avaje.ebean.Page;
import controllers.annotation.AnonymousCheck;
import controllers.annotation.IsAllowed;
import jxl.Workbook;
import jxl.format.ScriptStyle;
import jxl.format.UnderlineStyle;
import jxl.write.*;
import models.*;
import models.enumeration.Operation;
import models.support.ReviewSearchCondition;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.Tika;
import play.data.Form;
import play.db.ebean.Transactional;
import play.mvc.Controller;
import play.mvc.Result;
import utils.HttpUtil;
import utils.JodaDateUtil;
import views.html.reviewthread.list;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;

@AnonymousCheck
public class ReviewThreadApp extends Controller {

    public static final int REVIEWS_PER_PAGE = 15;

    @AnonymousCheck(requiresLogin = true, displaysFlashMessage = true)
    @IsAllowed(value = Operation.READ)
    @Transactional
    public static Result reviewThreads(String ownerName, String projectName) {
        Project project = Project.findByOwnerAndProjectName(ownerName, projectName);
        ReviewSearchCondition searchCondition = Form.form(ReviewSearchCondition.class).bindFromRequest().get();
        ExpressionList<CommentThread> el = searchCondition.asExpressionList(project);
        if ("xls".equals(request().getQueryString("format"))) {
            return reviewThreadsDownload(project, el);
        }
        Page<CommentThread> commentThreads = el.findPagingList(REVIEWS_PER_PAGE).getPage(searchCondition.pageNum - 1);
        return ok(list.render(project, commentThreads, searchCondition));
    }

    private static Result reviewThreadsDownload(Project project, ExpressionList<CommentThread> el) {
        List<CommentThread> commentThreads = el.findList();

        String filename = null;
        byte[] excelData = null;
        try {
            excelData = excelFrom(commentThreads);
            filename = HttpUtil.encodeContentDisposition(
                    project.name + "_reviews_" + JodaDateUtil.getDateStringWithoutSpace(new Date()) + ".xls");
        } catch (WriteException | IOException e) {
            e.printStackTrace();
        }

        response().setHeader("Content-Type", new Tika().detect(filename));
        response().setHeader("Content-Disposition", "attachment; " + filename);

        assert excelData != null;
        return ok(excelData);
    }

    public static byte[] excelFrom(List<CommentThread> commentThreads) throws WriteException, IOException {
        WritableWorkbook workbook;
        WritableSheet sheet;

        WritableCellFormat headerCellFormat = getHeaderCellFormat();
        WritableCellFormat bodyCellFormat = getBodyCellFormat();
        WritableCellFormat dateCellFormat = getDateCellFormat();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook = Workbook.createWorkbook(bos);
        sheet = workbook.createSheet(String.valueOf(JodaDateUtil.today().getTime()), 0);

        String[] titles = {"No", "COMMIT ID", "REVIEW ID", "REVIEW TITLE", "Thread Author", "Response Text", "Response", "REVIEW STATE", "is PullRequest?", "Date"};

        for (int i = 0; i < titles.length; i++) {
            sheet.addCell(new jxl.write.Label(i, 0, titles[i], headerCellFormat));
            sheet.setColumnView(i, 20);
        }

        int rowNumber = 0;
        for (int idx = 0; idx < commentThreads.size(); idx++) {
            CommentThread commentThread = commentThreads.get(idx);
            String commitId = "";
            if ( commentThread instanceof NonRangedCodeCommentThread){
                commitId = ((NonRangedCodeCommentThread) commentThread).commitId;
            } else {
                commitId = ((CodeCommentThread) commentThread).commitId;
            }
            String threadFirstComment = commentThread.getFirstReviewComment().getContents();
            for (int j = 0; j < commentThread.reviewComments.size(); j++) {
                ReviewComment comment = commentThread.reviewComments.get(j);
                int columnPos = 0;
                String reponseComment = threadFirstComment.equals(comment.getContents())? "":comment.getContents();
                sheet.addCell(new jxl.write.Label(columnPos++, rowNumber + 1, "" + (rowNumber + 1), bodyCellFormat));
                sheet.addCell(new jxl.write.Label(columnPos++, rowNumber + 1, commitId.substring(0, 7), bodyCellFormat));
                sheet.addCell(new jxl.write.Label(columnPos++, rowNumber + 1, commentThread.id.toString(), bodyCellFormat));
                sheet.addCell(new jxl.write.Label(columnPos++, rowNumber + 1, StringUtils.isEmpty(reponseComment)?threadFirstComment:"", bodyCellFormat));
                sheet.addCell(new jxl.write.Label(columnPos++, rowNumber + 1, StringUtils.isEmpty(reponseComment)?commentThread.author.name:"", bodyCellFormat));
                sheet.addCell(new jxl.write.Label(columnPos++, rowNumber + 1, reponseComment, bodyCellFormat));
                sheet.addCell(new jxl.write.Label(columnPos++, rowNumber + 1, StringUtils.isNotEmpty(reponseComment)?comment.author.name:"", bodyCellFormat));
                sheet.addCell(new jxl.write.Label(columnPos++, rowNumber + 1, commentThread.state.toString(), bodyCellFormat));
                sheet.addCell(new jxl.write.Label(columnPos++, rowNumber + 1, "" + commentThread.isOnPullRequest(), bodyCellFormat));
                sheet.addCell(new jxl.write.DateTime(columnPos++, rowNumber + 1, comment.createdDate, dateCellFormat));
                rowNumber++;
            }
        }
        workbook.write();

        try {
            workbook.close();
        } catch (WriteException | IOException e) {
            e.printStackTrace();
        }

        return bos.toByteArray();
    }

    private static String getIssueLabels(Issue issue) {
        StringBuilder labels = new StringBuilder();
        for(IssueLabel issueLabel: issue.getLabels()){
            labels.append(issueLabel.name).append(", ");
        }
        return labels.toString().replaceAll(", $", "");
    }

    private static WritableCellFormat getDateCellFormat() throws WriteException {
        WritableFont baseFont= new WritableFont(WritableFont.ARIAL, 12, WritableFont.NO_BOLD, false, UnderlineStyle.NO_UNDERLINE, jxl.format.Colour.BLACK, ScriptStyle.NORMAL_SCRIPT);
        DateFormat valueFormatDate = new DateFormat("yyyy-MM-dd HH:mm");
        WritableCellFormat cellFormat = new WritableCellFormat(valueFormatDate);
        cellFormat.setFont(baseFont);
        cellFormat.setShrinkToFit(true);
        cellFormat.setBorder(jxl.format.Border.ALL, jxl.format.BorderLineStyle.THIN);
        cellFormat.setAlignment(jxl.format.Alignment.CENTRE);
        cellFormat.setVerticalAlignment(jxl.format.VerticalAlignment.TOP);
        return cellFormat;
    }

    private static WritableCellFormat getBodyCellFormat() throws WriteException {
        WritableFont baseFont = new WritableFont(WritableFont.ARIAL, 12, WritableFont.NO_BOLD, false, UnderlineStyle.NO_UNDERLINE, jxl.format.Colour.BLACK, ScriptStyle.NORMAL_SCRIPT);
        return getBodyCellFormat(baseFont);
    }

    private static WritableCellFormat getBodyCellFormat(WritableFont baseFont) throws WriteException {
        WritableCellFormat cellFormat = new WritableCellFormat(baseFont);
        cellFormat.setBorder(jxl.format.Border.ALL, jxl.format.BorderLineStyle.THIN);
        cellFormat.setWrap(true);
        cellFormat.setVerticalAlignment(jxl.format.VerticalAlignment.TOP);
        return cellFormat;
    }

    private static WritableCellFormat getHeaderCellFormat() throws WriteException {
        WritableFont headerFont = new WritableFont(WritableFont.ARIAL, 14, WritableFont.BOLD, false,
                UnderlineStyle.NO_UNDERLINE, jxl.format.Colour.BLACK, ScriptStyle.NORMAL_SCRIPT);
        WritableCellFormat headerCell = new WritableCellFormat(headerFont);
        headerCell.setBorder(jxl.format.Border.ALL, jxl.format.BorderLineStyle.DOUBLE);
        headerCell.setAlignment(jxl.format.Alignment.CENTRE);
        return headerCell;
    }
}
