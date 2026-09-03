package com.github.yonaprojects.yona;

import java.security.MessageDigest;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

public class PasswordGenerator {
    public static void main(String[] args) throws Exception {
        String password = "1234";
        String salt = "f322603b";
        
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.reset();
        digest.update(salt.getBytes(StandardCharsets.UTF_8));
        byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
        for (int i = 1; i < 1024; i++) {
            digest.reset();
            hashed = digest.digest(hashed);
        }
        String result = Base64.getEncoder().encodeToString(hashed);
        System.out.println("GENERATED_PASSWORD_HASH:" + result);
    }
}
