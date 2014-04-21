/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Jungkook Kim
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
package utils;

import static org.junit.Assert.*;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import play.data.validation.Constraints.Validator;
import play.test.FakeApplication;
import play.test.Helpers;

/**
 * Reserved words Validator tests
 */
public class ReservedWordsValidatorTest {
    private Validator<String> validator = new ReservedWordsValidator();
    private static FakeApplication application;

    @BeforeClass
    public static void setUpBeforeClass() {
        application = support.Helpers.makeTestApplication();
        Helpers.start(application);
    }

    @AfterClass
    public static void tearDownAfterClass() {
        Helpers.stop(application);
        application = null;
    }

    @Test
    public void testReserved() {
        boolean valid = validator.isValid("favicon.ico");
        assertFalse(valid);
    }

    @Test
    public void testNotReserved() {
        boolean valid = validator.isValid("favicon");
        assertTrue(valid);
    }
}
