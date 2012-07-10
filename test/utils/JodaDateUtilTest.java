package utils;

import static org.fest.assertions.Assertions.assertThat;

import org.junit.Test;

public class JodaDateUtilTest {
	@Test
    public void today() {
		assertThat(JodaDateUtil.today()).isNotNull();
	}
}
