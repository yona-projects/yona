import com.unboundid.ldap.listener.*;
import com.unboundid.ldap.sdk.*;
import com.unboundid.util.ssl.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.logging.*;

/**
 * LDAP 로그인 대조용 인메모리 LDAP 서버. 받은 연결, 바인드, 검색(필터 포함)을 한 줄씩 기록한다.
 * 사용: java -cp unboundid-ldapsdk.jar LdapServer.java <포트> <기록파일>
 */
public class LdapServer {
    public static void main(String[] a) throws Exception {
        int port = Integer.parseInt(a[0]);
        Path log = Paths.get(a[1]);
        Files.write(log, new byte[0]);
        InMemoryDirectoryServerConfig cfg = new InMemoryDirectoryServerConfig("dc=example,dc=com");
        cfg.setSchema(null);
        cfg.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("default", port));
        cfg.setAllowedOperationTypes(OperationType.BIND, OperationType.SEARCH, OperationType.COMPARE, OperationType.EXTENDED);
        cfg.setAccessLogHandler(new Handler() {
            @Override public void publish(LogRecord r) {
                try { Files.write(log, (r.getMessage().replaceAll("conn=\\d+", "conn=N").replaceAll("op=\\d+", "op=N").replaceAll("\\d+ms", "Nms") + "\n").getBytes("UTF-8"), StandardOpenOption.APPEND); } catch (IOException e) { }
            }
            @Override public void flush() { }
            @Override public void close() { }
        });
        InMemoryDirectoryServer s = new InMemoryDirectoryServer(cfg);
        s.add("dn: dc=example,dc=com", "objectClass: top", "objectClass: domain", "dc: example");
        s.add("dn: ou=people,dc=example,dc=com", "objectClass: top", "objectClass: organizationalUnit", "ou: people");
        s.add("dn: ou=other,dc=example,dc=com", "objectClass: top", "objectClass: organizationalUnit", "ou: other");
        String[][] people = {
            // cn, uid, mail, displayName, givenName, password
            {"ldapalice", "ldapalice", "ldapalice@corp.example", "앨리스", "Alice", "alicepw"},
            {"ldapbob", "ldapbob", "ldapbob@corp.example", "Bob Builder", "Bob", "bobpw"},
            {"nomail", "nomail", "", "메일 없음", "NoMail", "nomailpw"},
            {"tester", "tester", "tester@corp.example", "LDAP Tester", "Tester", "ldaptesterpw"},      // 로컬 tester와 같은 ID
            {"g-guest", "g-guest", "guest@corp.example", "게스트", "Guest", "guestpw"},
            {"mixedCase", "mixedCase", "mixed@corp.example", "Mixed", "Mixed", "mixedpw"},
            {"한글사용자", "hangul", "hangul@corp.example", "한글 사용자", "Hangul", "hangulpw"},
            {"emaillogin", "emaillogin", "emaillogin@corp.example", "Email Login", "Email", "emailpw"},
            {"dupmail", "dupmail", "other@example.com", "Dup Mail", "Dup", "duppw"},                  // 로컬 other와 같은 이메일
            {"badmail", "badmail", "not-an-email", "Bad Mail", "Bad", "badpw"},
            {"blankname", "blankname", "blankname@corp.example", "", "", "blankpw"},
            {"emptypw", "emptypw", "emptypw@corp.example", "Empty Pw", "E", ""},
            {"locked", "locked", "locked@corp.example", "Locked", "L", "lockedpw"},
        };
        for (String[] p : people) {
            List<String> e = new ArrayList<>(List.of("dn: CN=" + p[0] + ",ou=people,dc=example,dc=com", "objectClass: top", "objectClass: person", "objectClass: organizationalPerson", "objectClass: inetOrgPerson",
                "cn: " + p[0], "sn: " + p[0], "uid: " + p[1], "sAMAccountName: " + p[1]));
            if (!p[2].isEmpty()) e.add("mail: " + p[2]);
            if (!p[3].isEmpty()) e.add("displayName: " + p[3]);
            if (!p[4].isEmpty()) e.add("givenName: " + p[4]);
            if (!p[5].isEmpty()) e.add("userPassword: " + p[5]);
            s.add(e.toArray(new String[0]));
        }
        s.add("dn: CN=otherou,ou=other,dc=example,dc=com", "objectClass: top", "objectClass: person", "objectClass: organizationalPerson", "objectClass: inetOrgPerson", "cn: otherou", "sn: x", "uid: otherou", "mail: otherou@corp.example", "displayName: Other OU", "userPassword: otherpw");
        s.startListening();
        System.out.println("LDAP 서버 시작 " + port);
        Thread.currentThread().join();
    }
}
