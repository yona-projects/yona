package com.feth.play.module.mail;

import org.apache.commons.mail.HtmlEmail;

import javax.inject.Singleton;
import java.util.Arrays;

@Singleton
public class Mailer {
    public void sendMail(Mail mail) {
        try {
            HtmlEmail email = new HtmlEmail();
            email.setSubject(mail.subject);
            email.setTextMsg(mail.body.text);
            email.setHtmlMsg(mail.body.html);
            email.setFrom(utils.Config.getEmailFromSmtp(), utils.Config.getSiteName());
            for (String recipient : mail.recipients) {
                email.addTo(recipient);
            }
            info.schleichardt.play2.mailplugin.Mailer.send(email);
        } catch (Exception e) {
            play.Logger.warn("Failed to send mail {} to {}", mail.subject, Arrays.toString(mail.recipients), e);
        }
    }

    public static String getEmailName(String email, String name) {
        return name == null || name.trim().isEmpty() ? email : name + " <" + email + ">";
    }

    public static class Mail {
        public final String subject;
        public final Body body;
        public final String[] recipients;

        public Mail(String subject, Body body, String[] recipients) {
            this.subject = subject;
            this.body = body;
            this.recipients = recipients;
        }

        public static class Body {
            public final String text;
            public final String html;

            public Body(String text, String html) {
                this.text = text;
                this.html = html;
            }
        }
    }
}
