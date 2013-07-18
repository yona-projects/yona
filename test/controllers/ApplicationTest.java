package controllers;

import org.junit.Test;
import play.mvc.Result;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.*;

import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.running;

public class ApplicationTest {
	
    @Test
    public void callIndex() {
	running(fakeApplication(support.Config.makeTestConfig()), new Runnable() {
    		@Override
	        public void run() {
		        Result result = callAction(controllers.routes.ref.Application.index());
		        assertThat(status(result)).isEqualTo(OK);
		        assertThat(contentType(result)).isEqualTo("text/html");
		        assertThat(charset(result)).isEqualTo("utf-8");
                assertThat(status(result)).isEqualTo(OK);
    		}
    	});
    }
}
