/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package utils;

import models.User;
import models.support.LdapUser;
import play.Play;

import javax.annotation.Nonnull;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import java.util.Hashtable;

public class LdapService {
    public static final boolean useLdap = Play.application().configuration().getBoolean("application.use.ldap.login.supoort",false);
    private static final String HOST = Play.application().configuration().getString("ldap.host", "127.0.0.1");
    private static final String PORT = Play.application().configuration().getString("ldap.port", "389");
    private static final String BASE_DN = Play.application().configuration().getString("ldap.baseDN", "");
    private static final String DN_POSTFIX = Play.application().configuration().getString("ldap.distinguishedNamePostfix", "");
    private static final String PROTOCOL = Play.application().configuration().getString("protocol", "ldap");
    private static final String LOGIN_PROPERTY = Play.application().configuration().getString("ldap.loginProperty", "sAMAccountName");
    private static final String DISPLAY_NAME_PROPERTY = Play.application().configuration().getString("ldap.displayNameProperty", "displayName");
    private static final String USER_NAME_PROPERTY = Play.application().configuration().getString("ldap.userNameProperty", "CN");
    private static final int TIMEOUT = 5000; //ms

    public LdapUser authenticate(String username, String password) throws NamingException {

        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put("com.sun.jndi.ldap.connect.timeout", ""+(TIMEOUT));
        env.put(Context.PROVIDER_URL, PROTOCOL + "://" + HOST + ":" + PORT);
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, getProperUsernameGuessing(username));
        env.put(Context.SECURITY_CREDENTIALS, password);

        DirContext authContext = new InitialDirContext(env);
        SearchResult searchResult = findUser(authContext, username, searchFilter(username));
        if (searchResult != null) {
            return getLdapUser(searchResult);
        } else {
            return null;
        }
    }

    private LdapUser getLdapUser(SearchResult searchResult) throws NamingException {
        Attributes attr = searchResult.getAttributes();
        return new LdapUser(attr.get(DISPLAY_NAME_PROPERTY),
                attr.get("mail"),
                attr.get(LOGIN_PROPERTY),
                attr.get("department"));
    }

    private String searchFilter(@Nonnull String username) {
        if(username.contains("@")){
            return "mail";
        } else {
            return LOGIN_PROPERTY;
        }
    }

    private String getProperUsernameGuessing(@Nonnull String username){
        if(username.contains("@")){
            return username;
        } else {
            User user = User.findByLoginId(username);
            if(!user.isAnonymous()) {
                return user.email;
            }
            return USER_NAME_PROPERTY + "=" + username + "," +  DN_POSTFIX;
        }
    }

    private SearchResult findUser(DirContext ctx, String username, String filter) throws NamingException {

        String searchFilter = "(" + filter + "=" + username + ")";

        SearchControls searchControls = new SearchControls();
        searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

        NamingEnumeration<SearchResult> results = ctx.search(BASE_DN, searchFilter, searchControls);

        SearchResult searchResult = null;
        if(results.hasMoreElements()) {
            searchResult = (SearchResult) results.nextElement();

            //make sure there is not another item available, there should be only 1 match
            if(results.hasMoreElements()) {
                System.err.println("Matched multiple users for the username: " + username);
                return null;
            }
        }

        return searchResult;
    }
}
