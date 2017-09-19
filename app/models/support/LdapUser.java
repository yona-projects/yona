/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package models.support;

import controllers.Application;
import org.apache.commons.lang3.StringUtils;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;

import java.util.ArrayList;
import java.util.List;

import static controllers.Application.GUEST_USER_LOGIN_ID_PREFIX;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class LdapUser {
    private Attribute displayName;
    private Attribute email;
    private Attribute userLoginId;
    private Attribute department;
    private Attribute englishName;

    public LdapUser(Attribute displayName, Attribute email, Attribute userLoginId,
                    Attribute department) {
        this.displayName = displayName;
        this.email = email;
        this.userLoginId = userLoginId;
        this.department = department;
    }

    public String getDisplayName() {
        if (isNotBlank(getDepartment())) {
            return getString(this.displayName) + " [" + getDepartment() + "]";
        } else {
            return getString(this.displayName);
        }
    }

    private String getString(Attribute attr) {
        try {
            if (attr == null || attr.get() == null){
                return "";
            } else {
                return attr.get().toString();
            }
        } catch (NamingException e) {
            e.printStackTrace();
            return "";
        }
    }

    public boolean isGuestUser() {
        if(isBlank(GUEST_USER_LOGIN_ID_PREFIX)){
            return false;
        }
        List<String> prefixes = new ArrayList<>();

        for(String idPrefix: GUEST_USER_LOGIN_ID_PREFIX.replaceAll(" ", "")
                .split(",")){
            String prefix = StringUtils.defaultString(idPrefix, "").toLowerCase().trim();
            if (isNotBlank(prefix)) {
                prefixes.add(prefix);
            }
        }

        for (String prefix : prefixes) {
            if(this.getUserLoginId().toLowerCase().startsWith(prefix.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    public String getEmail() {
        return getString(email);
    }

    public String getUserLoginId() {
        return getString(userLoginId);
    }

    public String getDepartment() {
        return getString(department);
    }

    public void setEnglishName(Attribute englishName) {
        this.englishName = englishName;
    }

    public String getEnglishName() {
        return getString(englishName);
    }

    @Override
    public String toString() {
        return "LdapUser{" +
                "displayName=" + displayName +
                ", email=" + email +
                ", userLoginId=" + userLoginId +
                ", department=" + department +
                ", englishName=" + englishName +
                '}';
    }
}
