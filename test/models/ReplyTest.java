package models;

import java.math.BigDecimal;

import org.junit.*;

import play.test.*;
import static play.test.Helpers.*;
import static org.fest.assertions.Assertions.*;
import static org.junit.Assert.*;

public class ReplyTest {
	
	private static FakeApplication app;

	@BeforeClass
	public static void startApp(){
		app = Helpers.fakeApplication(Helpers.inMemoryDatabase());
		Helpers.start(app);
	}
	@AfterClass
	public static void stopApp(){
		Helpers.stop(app);
	}

	private User testUser;
	
	@Before
	public void setUp()
	{
		testUser = User.findByName("hobi");
	}

	@Test
	public void testWrite() throws Exception {
		
		Article article = new Article();
		article.contents = "aa";
		article.title = "aaa";
		article.writerId = testUser.id;
		int articleNum = Article.write(article);
		assertThat(Article.findById(articleNum)).isNotNull();
		assertThat(Article.findById(articleNum).articleNum).isNotNull();
		
		Reply reply = new Reply();
		reply.articleNum = articleNum;
		reply.contents = "testThing";
		reply.writerId = testUser.id;
		
		long id = Reply.write(reply);
		
		assertThat(Reply.find.byId(id)).isNotNull();
	}
	
	@Test
	public void test() throws Exception {
		
	}
}
