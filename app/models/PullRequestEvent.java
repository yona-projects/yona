package models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Transient;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import controllers.UserApp;
import models.enumeration.EventType;
import models.enumeration.State;
import play.Configuration;
import play.db.ebean.Model;
import utils.JodaDateUtil;

/**
 * 보낸코드 이벤트 정보
 */
@Entity
public class PullRequestEvent extends Model implements TimelineItem {

    private static final long serialVersionUID = 1981361242582594128L;
    public static Finder<Long, PullRequestEvent> finder = new Finder<Long, PullRequestEvent>(Long.class, PullRequestEvent.class);

    @Id
    public Long id;
    public String senderLoginId;

    @ManyToOne
    public PullRequest pullRequest;

    @Enumerated(EnumType.STRING)
    public EventType eventType;

    public Date created;

    public String oldValue;
    public String newValue;

    @Override
    public Date getDate() {
        return created;
    }

    /**
     * NotiEvent를 사용하여 보낸코드 이벤트를 추가한다.
     *
     * 신규/보류/열림/병합
     *
     * @param notiEvent
     * @param pullRequest
     */
    public static void addEvent(NotificationEvent notiEvent, PullRequest pullRequest) {
        PullRequestEvent event = new PullRequestEvent();
        event.created = notiEvent.created;
        event.senderLoginId = notiEvent.getSender().loginId;
        event.pullRequest = pullRequest;
        event.eventType = notiEvent.eventType;
        event.oldValue = notiEvent.oldValue;
        event.newValue = notiEvent.newValue;

        event.save();
    }


    public static void addStateEvent(PullRequest pullRequest, State state) {
        PullRequestEvent event = new PullRequestEvent();
        event.created = JodaDateUtil.now();
        event.senderLoginId = UserApp.currentUser().loginId;
        event.pullRequest = pullRequest;
        event.eventType = EventType.PULL_REQUEST_STATE_CHANGED;
        event.newValue = state.state();

        event.save();
    }

    /**
     * 보낸코드 병합 결과 이벤트를 추가한다.
     *
     * 충돌/충돌 해결
     *
     * @param sender
     * @param eventType
     * @param state
     * @param pullRequest
     */
    public static void addMergeEvent(User sender, EventType eventType, State state, PullRequest pullRequest) {
        PullRequestEvent event = new PullRequestEvent();
        event.created = new Date();
        event.senderLoginId = sender.loginId;
        event.pullRequest = pullRequest;
        event.eventType = eventType;
        event.newValue = state.state();

        event.save();
    }

    /**
     * 보낸코드 커밋 이벤트를 추가한다.
     *
     * @param sender
     * @param pullRequest
     * @param commits
     */
    public static void addCommitEvents(User sender, PullRequest pullRequest, List<PullRequestCommit> commits) {
        Date createdDate = new Date();
        PullRequestEvent event = new PullRequestEvent();
        event.created = createdDate;
        event.senderLoginId = sender.loginId;
        event.pullRequest = pullRequest;
        event.eventType = EventType.PULL_REQUEST_COMMIT_CHANGED;
        event.newValue = StringUtils.EMPTY;

        for (int i = 0; i < commits.size(); i++) {
            event.newValue += commits.get(i).id;
            if (i != commits.size() - 1) {
                event.newValue += PullRequest.DELIMETER;
            }
        }

        event.save();
    }

    /**
     * 보낸코드의 이벤트 목록을 가져온다.
     * @param pullRequest
     * @return
     */
    public static List<PullRequestEvent> findByPullRequest(PullRequest pullRequest) {
        return finder.where().eq("pullRequest", pullRequest).findList();
    }

    /**
     * 이벤트에 저장된 커밋아이디로 커밋목록을 가져온다.
     * @return
     */
    @Transient
    public List<PullRequestCommit> getPullRequestCommits() {
        List<PullRequestCommit> commits = new ArrayList<PullRequestCommit>();

        String[] commitIds = this.newValue.split(PullRequest.DELIMETER);
        for (String commitId: commitIds) {
            commits.add(PullRequestCommit.findById(commitId));
        }

        Collections.sort(commits, TimelineItem.DESC);

        return commits;
    }
}
