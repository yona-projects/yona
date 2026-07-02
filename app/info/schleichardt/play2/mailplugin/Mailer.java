package info.schleichardt.play2.mailplugin;

import org.apache.commons.mail.Email;

public final class Mailer {
    private Mailer() {
    }

    public static String send(Email email) throws Exception {
        configure(email);
        if (play.Configuration.root().getBoolean("smtp.mock", true)) {
            org.slf4j.LoggerFactory.getLogger("mail").info("SMTP mock mode is enabled; skipped sending {}", email.getSubject());
            return "mock";
        }
        return email.send();
    }

    static void configure(Email email) throws Exception {
        play.Configuration config = play.Configuration.root();
        String host = config.getString("smtp.host");
        if (host != null) {
            email.setHostName(host);
        }
        Integer port = config.getInt("smtp.port");
        if (port != null) {
            email.setSmtpPort(port);
        }
        email.setSSLOnConnect(config.getBoolean("smtp.ssl", false));
        String user = config.getString("smtp.user");
        String password = config.getString("smtp.password");
        if (user != null && password != null) {
            email.setAuthentication(user, password);
        }
        email.setCharset("utf-8");
    }
}
