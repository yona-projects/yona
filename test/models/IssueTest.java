package models;

import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.running;
import static org.fest.assertions.Assertions.*;

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
	/*
	
	 @Test
	    public void findById() {
	        running(fakeApplication(), new Runnable() {
	           public void run() {
	               Issue user1 = Issue.find.byId(1l);
	               assertThat(user1.title).isEqualTo("불필요한 로그 출력 코드 제거");
	               
	           }
	        });
	    }
	*/

}
