package utils;

import static org.junit.Assert.*;
import static org.fest.assertions.Assertions.assertThat;

import java.io.UnsupportedEncodingException;

import models.User;

import org.apache.commons.codec.binary.Base64;
import org.junit.Test;

import utils.BasicAuthAction;

public class BasicAuthActionTest {

    @Test
    public void parseCredentials() {
        // credentials = "Basic" basic-credentials
        // basic-credentials = base64-user-pass
        // base64-user-pass = <base64 [4] encoding of user-pass,
        // user-pass = userid ":" password
        // userid = *<TEXT excluding ":">
        // password = *TEXT
        
        // ok
        String userpass = "hello:world";
        String basicCredentials = new String(Base64.encodeBase64(userpass.getBytes()));
        try {
            User user = BasicAuthAction.parseCredentials("Basic " + basicCredentials);
            assertThat(user.loginId).isEqualTo("hello");
            assertThat(user.password).isEqualTo("world");
        } catch (UnsupportedEncodingException e) {
            fail();
        } catch (MalformedCredentialsException e) {
            fail();
        }
        
        // ok
        userpass = ":";
        basicCredentials = new String(Base64.encodeBase64(userpass.getBytes()));
        try {
            User user = BasicAuthAction.parseCredentials("Basic " + basicCredentials);
            assertThat(user.loginId).isEqualTo("");
            assertThat(user.password).isEqualTo("");
        } catch (UnsupportedEncodingException e) {
            fail();
        } catch (MalformedCredentialsException e) {
            fail();
        }
        
        // malformed credentials.
        String malformedUserpass = "helloworld";
        String malformedCredentials = new String(Base64.encodeBase64(malformedUserpass.getBytes()));
        try {
            BasicAuthAction.parseCredentials("Basic " + malformedCredentials);
            fail();
        } catch (UnsupportedEncodingException e) {
            fail();
        } catch (MalformedCredentialsException e) {
            // success
        }
        
        // username and password decoded by only ISO-8859-1
        // NOTE: UnsupportedEncodingException is NOT thrown here. It should be thrown
        // if and only if the Server does not support ISO-8859-1.
        malformedUserpass = "안녕:세상";
        malformedCredentials = new String(Base64.encodeBase64(malformedUserpass.getBytes()));
        try {
            User user = BasicAuthAction.parseCredentials("Basic " + malformedCredentials);
            assertThat(user.loginId).isNotEqualTo("안녕");
            assertThat(user.password).isNotEqualTo("세상");
        } catch (UnsupportedEncodingException e) {
            fail();
        } catch (MalformedCredentialsException e) {
            fail();
        }
    }
    
}