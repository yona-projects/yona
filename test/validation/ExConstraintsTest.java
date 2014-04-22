/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Daegeun Kim
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

