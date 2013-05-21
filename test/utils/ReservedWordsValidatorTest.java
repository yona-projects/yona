package utils;

import static org.junit.Assert.*;

import org.junit.Test;

import play.data.validation.Constraints.Validator;

/**
 * Reserved words Validator tests
 * 
 * @author kjkmadness
 */
public class ReservedWordsValidatorTest {
    private Validator<String> validator = new ReservedWordsValidator();

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