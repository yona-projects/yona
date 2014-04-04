package models;

import models.enumeration.State;
import play.data.validation.Constraints;

import java.util.List;

public class IssueMassUpdate {
    public State state;
    public User assignee;
    public Milestone milestone;
    public boolean delete;

    @Constraints.Required
    public List<Issue> issues;
    public List<IssueLabel> attachingLabel;
    public List<IssueLabel> detachingLabel;
}
