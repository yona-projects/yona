/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

package models;

import models.enumeration.ResourceType;
import models.resource.Resource;
import play.db.ebean.Model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static models.enumeration.ResourceType.ISSUE_COMMENT;
import static models.enumeration.ResourceType.ISSUE_POST;

@Entity
public class Mention extends Model {
    private static final long serialVersionUID = 5803239458057753468L;

    public static final Finder<Long, Mention> find = new Finder<>(Long.class, Mention.class);

    @Id
    public Long id;

    public ResourceType resourceType;

    public String resourceId;

    @ManyToOne
    public User user;

    /**
     * Store the list of mentioned users.
     *
     * Yobi keeps the list of mentioned users to find them quickly. Every resource mentioning
     * users MUST add them by using this method. If not some features like "Issues Mentioning
     * You" ignore the mention.
     *
     * @param resource the resource mentioning the users
     * @param mentionedUsers the users mentioned by the resource
     */
    public static void update(Resource resource, Set<User> mentionedUsers) {
        for (Mention mention : find.where().eq("resourceType", resource.getType()).eq("resourceId",
                resource.getId()).findList()) {
            if (mentionedUsers.contains(mention.user)) {
                mentionedUsers.remove(mention.user);
            } else {
                mention.delete();
            }
        }

        for (User user : mentionedUsers) {
            Mention mention = new Mention();
            mention.resourceId = resource.getId();
            mention.resourceType = resource.getType();
            mention.user = user;
            mention.save();
        }
    }

    public static List<Long> getMentioningIssueIds(Long mentionUserId) {
        Set<Long> ids = new HashSet<>();
        Set<Long> commentIds = new HashSet<>();

        for (Mention mention : Mention.find.where()
                .eq("user.id", mentionUserId)
                .in("resourceType", ISSUE_POST, ISSUE_COMMENT)
                .findList()) {

            switch (mention.resourceType) {
                case ISSUE_POST:
                    ids.add(Long.valueOf(mention.resourceId));
                    break;
                case ISSUE_COMMENT:
                    commentIds.add(Long.valueOf(mention.resourceId));
                    break;
                default:
                    play.Logger.warn("'" + mention.resourceType + "' is not supported.");
                    break;
            }
        }

        if (!commentIds.isEmpty()) {
            for (IssueComment comment : IssueComment.find.where()
                    .idIn(new ArrayList<>(commentIds))
                    .findList()) {
                ids.add(comment.issue.id);
            }
        }

        return new ArrayList<>(ids);
    }
}
