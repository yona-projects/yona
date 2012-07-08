package models;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class IssueTest {

    @Test
    public void testCreate() {
        Issue issue = new Issue();
        issue.setTitle("Bug on test page");
        issue.setBody("There is javascript erron on line 75.");

        Issue.create(issue);
        Issue actualIssue = Issue.findById(issue.getId());

        assertEquals("Bug on test page", actualIssue.getTitle());
        assertEquals("There is javascript erron on line 75.", actualIssue.getBody());
    }

}
