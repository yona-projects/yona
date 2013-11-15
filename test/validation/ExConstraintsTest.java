package validation;

import java.util.Map;
import java.util.HashMap;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import play.data.Form;

import static org.fest.assertions.Assertions.assertThat;

public class ExConstraintsTest {
    @Test
    public void testReservedWords() {
        Form<Model> form = new Form<>(Model.class);
        Model model = form.bind(newMap("name10")).get();

        assertThat(model.name).isEqualTo("name10");
    }

    @Test(expected = IllegalStateException.class)
    public void testReservedWordsThrowIllegalStateException() {
        Form<Model> form = new Form<>(Model.class);
        Model model = form.bind(newMap("..")).get(); // one of reserved words
        // throw
    }

    // for test
    private Map<String, String> newMap(String name) {
    	Map<String, String> map = new HashMap<>();
    	map.put("name", name);
    	return map;
    }

    // for test
    public static class Model {
    	@ExConstraints.Restricted({".", "..", ".git"})
    	private String name;

    	public Model() {
    	}

    	public String getName() {
    		return name;
    	}

    	public void setName(String name) {
    		this.name = name;
    	}
    }
}

