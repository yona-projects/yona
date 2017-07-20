/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/

package models;

import org.apache.commons.lang3.StringUtils;
import play.Play;

// Simple DTO for automatic user creation
public class CandidateUser {
    private String name;
    private String email;
    private String loginId;
    private String password;
    private boolean isGuest;

    public CandidateUser(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public CandidateUser(String name, String email, String loginId, String password, boolean isGuest) {
        this.name = name;
        this.email = email;
        this.loginId = loginId;
        this.password = password;
        this.isGuest = isGuest;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        if (StringUtils.isBlank(this.email)) {
            return "";
        }
        return email;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPassword() {
        return password;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isGuest() {
        return isGuest;
    }

    @Override
    public String toString() {
        return "CandidateUser{" +
                "name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", loginId='" + loginId + '\'' +
                ", password='" + password + '\'' +
                '}';
    }
}
