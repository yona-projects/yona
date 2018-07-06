/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
package models;

import play.db.ebean.Model;
import utils.TemplateHelper;

import javax.persistence.*;
import java.util.List;

@Entity
public class TitleHead extends Model {

    private static final long serialVersionUID = 5194690128303455482L;

    public static final Finder<Long, TitleHead> finder = new Finder<>(Long.class, TitleHead.class);

    @Id
    public Long id;

    @ManyToOne
    public Project project;

    public String headKeyword;

    public int frequency;

    public static List<TitleHead> findByProject(Project project, String query) {
        return finder.where()
                .eq("project.id", project.id)
                .ilike("headKeyword", "%" +query + "%")
                .findList();
    }

    public static TitleHead findByHeadKeyword(Project project, String headKeyword) {
        List<TitleHead> founds = finder.where()
                .eq("project.id", project.id)
                .eq("headKeyword", headKeyword).findList();
        if (founds.size() > 0) {
            return founds.get(0);
        } else {
            return null;
        }
    }

    public static void newHeadKeyword(Project project, String headKeyword) {
        TitleHead found = findByHeadKeyword(project, headKeyword);
        if (found != null) {
            found.frequency++;
            found.update();
        } else {
            TitleHead titleHeadKeyword = new TitleHead();
            titleHeadKeyword.project = project;
            titleHeadKeyword.headKeyword = headKeyword;
            titleHeadKeyword.frequency = 1;
            titleHeadKeyword.save();
        }
    }

    public static void reduceHeadKeyword(Project project, String headKeyword) {
        TitleHead found = findByHeadKeyword(project, headKeyword);
        if (found != null) {
            found.frequency--;
            if (found.frequency == 0) {
                found.delete();
            } else {
                found.update();
            }
        }
    }

    public static void saveTitleHeadKeyword(Project project, String title) {
        String[] headKeywords = TemplateHelper.extractHeaderWordsInBrackets(title);
        for (String headKeyword : headKeywords) {
            String trimmed = headKeyword.trim();

            if (isSurroundedByBracket(trimmed)) {
                newHeadKeyword(project, removeBracket(trimmed));
            }
        }
    }

    private static String removeBracket(String trimmed) {
        return trimmed.substring(1, trimmed.length() - 1);
    }

    private static boolean isSurroundedByBracket(String trimmed) {
        return trimmed.indexOf("[") == 0
                && trimmed.indexOf("]") == trimmed.length() - 1
                && trimmed.length() > 2;
    }

    public static void deleteTitleHeadKeyword(Project project, String title) {
        String[] headKeywords = TemplateHelper.extractHeaderWordsInBrackets(title);
        for (String headKeyword : headKeywords) {
            String trimmed = headKeyword.trim();

            if (isSurroundedByBracket(trimmed)) {
                reduceHeadKeyword(project, removeBracket(trimmed));
            }
        }
    }
}

