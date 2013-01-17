package utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.apache.commons.mail.MultiPartEmail;

import play.Application;
//import scala.actors.threadpool.Arrays;

public class Mailer {
    private final String smtpHost;
    private final Integer smtpPort;
    private final Boolean smtpSsl;
    private final Boolean smtpTls;
    private final String smtpUser;
    private final String smtpPassword;

    private final Map<String, List<String>> context = new HashMap<String, List<String>>();

    public Mailer(Application app) {
        smtpHost = app.configuration().getString("smtp.host");
        smtpPort = app.configuration().getInt("smtp.port");
        smtpSsl = app.configuration().getBoolean("smtp.ssl");
        smtpTls = app.configuration().getBoolean("smtp.tls");
        smtpUser = app.configuration().getString("smtp.user");
        smtpPassword = app.configuration().getString("smtp.password");
    }

    public String send(String bodyText) throws EmailException {
        return send(bodyText, "");
    }

    public String sendHtml(String bodyHtml) throws EmailException {
        return send("", bodyHtml);
    }

    public String send(String bodyText, String bodyHtml) throws EmailException {
        Email email = createEmailer(bodyText, bodyHtml);
        email.setCharset(headOption("charset", "utf-8"));
        email.setSubject(headOption("subject", ""));

        for (String fromEmail : e("from")) {
            email.setFrom(addressFrom(fromEmail), nameFrom(fromEmail)); }
        for(String replyToEmail : e("replyTo")) {
            email.addReplyTo(addressFrom(replyToEmail), nameFrom(replyToEmail)); }
        for(String recipient: e("recipients")) {
            email.addTo(addressFrom(recipient), nameFrom(recipient)); }
        for(String ccRecipient: e("ccRecipients")) {
            email.addCc(addressFrom(ccRecipient), nameFrom(ccRecipient)); }
        for(String bccRecipient: e("bccRecipients")) {
            email.addBcc(addressFrom(bccRecipient), nameFrom(bccRecipient)); }

        Map<String, String> eheader = eheader();
        Iterator<String> it = eheader.keySet().iterator();
        while(it.hasNext()) {
            String name = it.next();
            String value = eheader.get(name);
            email.addHeader(name, value);
        }
        email.setHostName(smtpHost);
        if (smtpPort != null) {
            email.setSmtpPort(smtpPort);
        }
        if (smtpSsl != null) {
            email.setSSL(smtpSsl);
        }
        if (smtpTls != null) {
            email.setTLS(smtpTls);
        }
        email.setAuthenticator(new DefaultAuthenticator(smtpUser, smtpPassword));
        email.setDebug(false);
        return email.send();
    }

    private String addressFrom(String emailAddress) {
        return internetAddress(emailAddress).getAddress();
    }

    private String nameFrom(String emailAddress) {
        return internetAddress(emailAddress).getPersonal();
    }

    private InternetAddress internetAddress(String emailAddress) {
        InternetAddress iAddress = null;
        if (emailAddress != null) {
            try {
                iAddress = new InternetAddress(emailAddress);
            } catch (AddressException e) {
                e.printStackTrace();
            }
        }
        return iAddress;
    }

    private String headOption(String key, String defaultValue) {
        List<String> values = e(key);
        if (values.size() == 1) {
            return values.get(0);
        } else {
            return defaultValue;
        }
    }

    /**
     * Creates an appropriate email object based on the content type.
     *
     * @param bodyText
     * @param bodyHtml
     * @return
     * @throws EmailException
     */
    private Email createEmailer(String bodyText, String bodyHtml)
            throws EmailException {
        Email email = null;
        if (bodyHtml == null || bodyHtml == "") {
            email = new MultiPartEmail().setMsg(bodyText);
        } else {
            email = new HtmlEmail().setHtmlMsg(bodyHtml).setTextMsg(bodyText);
        }
        return email;
    }

    private Map<String, String> eheader() {
        Map<String, String> headers = new HashMap<String, String>();
        Iterator<String> it = context.keySet().iterator();
        while(it.hasNext()) {
            String key = it.next();
            if(key.contains("header-")) {
                headers.put(key.substring(7), context.get(key).get(0));
            }
        }
        return headers;
    }

    /**
     * extract parameter key from context
     *
     * @param key
     */
    private List<String> e(String key) {
        return context.containsKey(key) ? context.get(key)
                : Collections.EMPTY_LIST;
    }

    /**
     * Sets a subject for this email. It enables formatting of the providing
     * string using Java's string formatter.
     *
     * @param subject
     * @param args
     *            TODO
     */
    public void setSubject(String... subject) {
        context.put("subject", Arrays.asList(subject));
    }

    /**
     * Adds an email recipient ("to" addressee).
     *
     * @param recipients
     */
    public void addRecipient(String... recipient) {
        context.put("recipients", Arrays.asList(recipient));
    }

    /**
     * Defines the sender of this email("from" address).
     *
     * @param from
     */
    public void addFrom(String from) {
        context.put("from", list(from));
    }

    private List<String> list(String string) {
        List<String> list = new ArrayList<String>();
        list.add(string);
        return list;
    }

    /**
     * Adds an email recipient in CC.
     *
     * @param ccRecipients
     */
    public void addCc(String... ccRecipients) {
        context.put("ccRecipients", Arrays.asList(ccRecipients));
    }

    /**
     * Adds an email recipient in BCC.
     *
     * @param bccRecipients
     */
    public void addBcc(String... bccRecipients) {
        context.put("bccRecipients", Arrays.asList(bccRecipients));
    }

    /**
     * Defines the "reply to" email address.
     *
     * @param replyTo
     */
    public void setReplyTo(String replyTo) {
        context.put("replyTo", list(replyTo));
    }

    /**
     * Sets the charset for this email.
     *
     * @param charset
     */
    public void setCharset(String charset) {
        context.put("charset", list(charset));
    }

    /**
     * Adds a request header to this email message.
     *
     * @param key
     * @param value
     */
    public void addHeader(String key, String value) {
        context.put("header-" + key, list(value));
    }

}
