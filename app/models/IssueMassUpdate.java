package models;

import models.enumeration.State;

import java.util.List;

public class IssueMassUpdate {
    public State state;
    public User assignee;
    public Milestone milestone;
    public List<Issue> issues;
}
