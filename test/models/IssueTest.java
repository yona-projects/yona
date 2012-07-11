package models;

import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.running;
import static org.fest.assertions.Assertions.*;

import java.util.List;

import org.junit.Test;

public class IssueTest extends ModelTest{

	@Test
	public void create() {
		Issue issue = new Issue();
		issue.title = "불필요한 로그 출력 코드 제거";
//		issue.date = "";
		issue.status = Issue.STATUS_ENROLLED;
		issue.userId = 1l;
		assertThat(Issue.create(issue)).isEqualTo(5l);
	}
	

	@Test
	public void findById() throws Exception {
		Issue issue = new Issue();
		issue.title = "불필요한 로그 출력 코드 제거";
//		issue.date = "";
		issue.status = Issue.STATUS_ENROLLED;
		issue.userId = 1l;
		
		Long id = Issue.create(issue);

		Issue issueTest = Issue.findById(id);
		assertThat(issueTest).isNotNull();
	}
	
	@Test
	public void delete() {
		Issue issue = new Issue();
		issue.title = "지우기 테스트";
//		issue.date = "";
		issue.status = Issue.STATUS_ENROLLED;
		issue.userId = 2l;
				
		Issue.create(issue);
		Issue.delete(5l);
				
		assertThat(Issue.findById(5l)).isNull();
	}
	
	@Test
    public void findByTitle() {
		Issue issue = new Issue();
		issue.title = "findByTitle check";
//		issue.date = "";
		issue.status = Issue.STATUS_CLOSED;
		issue.userId = 4l;
		
		Long id = Issue.create(issue);

		List<Issue> issueTest = Issue.findByTitle("check");
		assertThat(issueTest).hasSize(1);
	}
	

}
