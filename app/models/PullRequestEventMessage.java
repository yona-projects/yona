package models;

import play.mvc.Http.Request;

/**
 * 보낸코드 충돌 검사 및 알림/이벤트 추가를 위한 정보들을 전달
 */
public class PullRequestEventMessage {
    private User sender;
    private Request request;
    private Project project;
    private String branch;

    public PullRequestEventMessage(User sender, Request request, Project project, String branch) {
        this.sender = sender;
        this.request = request;
        this.project = project;
        this.branch = branch;
    }

    public User getSender() {
        return sender;
    }

    public Request getRequest() {
        return request;
    }

    public Project getProject() {
        return project;
    }

    public String getBranch() {
        return branch;
    }
}
