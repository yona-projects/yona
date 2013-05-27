package models;

import models.enumeration.State;
import play.data.validation.Constraints;

import java.util.List;

public class IssueMassUpdate {
    public State state;
    public User assignee;
    public Milestone milestone;

    @Constraints.Required
    public List<Issue> issues;
}
