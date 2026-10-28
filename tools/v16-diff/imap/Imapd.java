import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;

/** 시험용 IMAP 서버(GreenMail). 사용: java -cp <jar들> Imapd.java <포트> <사용자> <비밀번호> */
public class Imapd {
    public static void main(String[] a) throws Exception {
        int port = Integer.parseInt(a[0]);
        GreenMail gm = new GreenMail(new ServerSetup(port, "127.0.0.1", "imap"));
        gm.setUser(a[1], a[1], a[2]);
        gm.start();
        System.out.println("IMAP " + port + " 시작");
        Thread.currentThread().join();
    }
}
