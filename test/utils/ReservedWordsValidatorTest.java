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
 * 
 * @author kjkmadness
 */
public class ReservedWordsValidatorTest {
    private Validator<String> validator = new ReservedWordsValidator();
    private static FakeApplication application;

    @BeforeClass
    public static void setUpBeforeClass() {
        application = Helpers.fakeApplication(support.Config.makeTestConfig());
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