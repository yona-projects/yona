package models;

import play.mvc.Http.Request;

/**
 * 충돌 검사 및 알림을 위한 정보들을 전달
 */
public class ConflictCheckMessage {
    private User sender;
    private Request request;
    private Project project;
    private String branch;

    public ConflictCheckMessage(User sender, Request request, Project project, String branch) {
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
