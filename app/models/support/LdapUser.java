/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package models.support;

import org.apache.commons.lang3.StringUtils;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;

public class LdapUser {
    private Attribute displayName;
    private Attribute email;
    private Attribute userLoginId;
    private Attribute department;

    public LdapUser(Attribute displayName, Attribute email, Attribute userLoginId, Attribute department) {
        this.displayName = displayName;
        this.email = email;
        this.userLoginId = userLoginId;
        this.department = department;
    }

    public String getDisplayName() {
        if (StringUtils.isNotBlank(getDepartment())) {
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

    public String getEmail() {
        return getString(email);
    }

    public String getUserLoginId() {
        return getString(userLoginId);
    }

    public String getDepartment() {
        return getString(department);
    }

    @Override
    public String toString() {
        return "LdapUser{" +
                "displayName='" + getDisplayName() + '\'' +
                ", email='" + getEmail() + '\'' +
                ", userId='" + getUserLoginId() + '\'' +
                ", department='" + getDepartment() + '\'' +
                '}';
    }
}
