/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @Author Yi EungJun
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package mailbox;

import akka.actor.Cancellable;
import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPMessage;
import com.sun.mail.imap.IMAPStore;
import models.Property;
import models.User;
import play.Configuration;
import play.Logger;
import play.libs.Akka;
import scala.concurrent.duration.Duration;
import utils.Diagnostic;
import utils.SimpleDiagnostic;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.NotThreadSafe;
import javax.mail.Folder;
import javax.mail.FolderClosedException;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.event.MessageCountEvent;
import javax.mail.event.MessageCountListener;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static javax.mail.Session.getDefaultInstance;

/**
 * MailboxService opens a mailbox and process emails if necessary.
 *
 * MailboxService connects an IMAP server and opens the mailbox from the
 * server. Every configuration to be needed to do that is defined by imap.* in
 * conf/application.conf.
 *
 * Then MailboxService fetches and processes emails in the mailbox as follows:
 *
 * 1. Only emails whose recipients contain the address of Yobi defined by
 *    imap.address configuration are accepted.
 * 2. Emails must have one or more recipients which are Yobi projects; If not
 *    Yobi replies with an error.
 * 3. Emails which reference or reply to a resource assumed to comment the
 *    resource; otherwise assumed to post an issue in the projects.
 * 4. Yobi does the assumed job only if the sender has proper permission to do
 *    that; else Yobi replies with a permission denied error.
 *
 * Note: It is possible to create multiple resources if the recipients contain
 * multiple projects.
 */
@NotThreadSafe
public class MailboxService {
    private IMAPStore store;
    private static IMAPFolder folder;
    private Thread idleThread;
    private Cancellable pollingSchedule;
    private boolean isStopping = false;

    /**
     * Among the given {@code keys}, returns the keys which don't have a matched
     * value in the given {@code config}.
     *
     * @param   config
     * @param   keys
     * @return  the keys which don't have a matched value
     */
    private static Set<String> getNotConfiguredKeys(Configuration config, String... keys) {
        Set<String> notConfigured = new HashSet<>();
        Map<String, Object> configMap = config.asMap();
        for (String key : keys) {
            if (!configMap.containsKey(key)) {
                notConfigured.add(key);
            }
        }
        return notConfigured;
    }

    /**
     * Connect to the IMAP server configured by the given {@code imapConfig}.
     *
     * @param imapConfig
     * @return the store to be connected
     * @throws MessagingException
     */
    private IMAPStore connect(Configuration imapConfig) throws MessagingException {
        Set<String> notConfiguredKeys =
                getNotConfiguredKeys(imapConfig, "host", "user", "password");

        if (!notConfiguredKeys.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot connect to the IMAP server because these are" +
                            " not configured: " + notConfiguredKeys);
        }

        Properties props = new Properties();
        String s = imapConfig.getBoolean("ssl", false) ? "s" : "";
        props.setProperty("mail.store.protocol", "imap" + s);

        Session session = getDefaultInstance(props, null);
        store = (IMAPStore) session.getStore();
        store.connect(imapConfig.getString("host"),
                imapConfig.getString("user"),
                imapConfig.getString("password"));
        return store;

    }

    /**
     * Stop MailboxService.
     */
    public void stop() {
        isStopping = true;
        try {
            folder.close(true);
            store.close();
            if (pollingSchedule != null && !pollingSchedule.isCancelled()) {
                pollingSchedule.cancel();
            }
        } catch (MessagingException e) {
            play.Logger.error("Error occurred while stop the email receiver", e);
        }
    }

    /**
     * Start Mailbox Service.
     */
    public void start() {
        final Configuration imapConfig = Configuration.root().getConfig("imap");

        if (imapConfig == null) {
            play.Logger.info("Mailbox Service doesn't start because IMAP server is not configured.");
            return;
        }

        List<User> users = User.find.where()
                .ilike("email", imapConfig.getString("user") + "+%").findList();

        if (users.size() == 1) {
            Logger.warn("There is a user whose email is danger: " + users);
        }
        if (users.size() > 1) {
            Logger.warn("There are some users whose email is danger: " + users);
        }

        try {
            store = connect(imapConfig);
            folder = (IMAPFolder) store.getFolder(
                    imapConfig.getString("folder", "inbox"));
            folder.open(Folder.READ_ONLY);
        } catch (Exception e) {
            play.Logger.error("Failed to open IMAP folder", e);
            return;
        }

        try {
            EmailHandler.handleNewMessages(folder);
        } catch (MessagingException e) {
            play.Logger.error("Failed to handle new messages");
        }

        try {
            startEmailListener();
        } catch (Exception e) {
            startEmailPolling();
        }

        Diagnostic.register(new SimpleDiagnostic() {
            @Override
            public String checkOne() {
                if (idleThread == null) {
                    return "The Email Receiver is not initialized";
                } else if (!idleThread.isAlive()) {
                    return "The Email Receiver is not running";
                } else {
                    return null;
                }
            }
        });
    }

    /**
     * Reopen the IMAP folder which is used by MailboxService.
     *
     * @return  the open IMAP folder
     * @throws  MessagingException
     */
    private IMAPFolder reopenFolder() throws MessagingException {
        final Configuration imapConfig = Configuration.root().getConfig("imap");

        if (store == null || !store.isConnected()) {
            store = connect(imapConfig);
        }

        IMAPFolder folder = (IMAPFolder) store.getFolder(
                imapConfig.getString("folder", "inbox"));
        folder.open(Folder.READ_ONLY);

        return folder;
    }

    /**
     * Start the polling of emails.
     *
     * The polling is fetching new emails from the IMAP folder and processing
     * them.
     *
     * This polling is a fallback of
     * {@link #startEmailListener()} if the IMAP server does
     * not support IDLE command.
     */
    private void startEmailPolling() {
        Runnable polling = new Runnable() {
            @Override
            public void run() {
                try {
                    if (folder == null || !folder.isOpen()) {
                        folder = reopenFolder();
                    }

                    EmailHandler.handleNewMessages(folder);
                } catch (MessagingException e) {
                    play.Logger.error("Failed to poll emails", e);
                    return;
                }

                try {
                    folder.close(true);
                } catch (MessagingException e) {
                    play.Logger.error("Failed to close the IMAP folder", e);
                }
            }
        };

        pollingSchedule = Akka.system().scheduler().schedule(
                Duration.create(0, TimeUnit.MINUTES),
                Duration.create(
                        Configuration.root().getMilliseconds("application.mailbox.polling.interval", 5 * 60 * 1000L),
                        TimeUnit.MILLISECONDS),
                polling,
                Akka.system().dispatcher()
        );
    }

    /**
     * Start the email listener by using IDLE command.
     *
     * The listener will fetch new emails from the IMAP folder and process them.
     *
     * @throws MessagingException
     * @throws UnsupportedOperationException
     */
    private void startEmailListener()
            throws MessagingException, UnsupportedOperationException {
        if (!((IMAPStore)folder.getStore()).hasCapability("IDLE")) {
            throw new UnsupportedOperationException(
                    "The imap server does not support IDLE command");
        }

        MessageCountListener messageCountListener = new MessageCountListener() {
            @Override
            public void messagesAdded(@Nonnull MessageCountEvent e) {
                try {
                    EmailHandler.handleMessages(folder, e.getMessages());
                } catch (Exception e1) {
                    play.Logger.error("Unexpected error occurs while handling messages", e1);
                }
            }

            @Override
            public void messagesRemoved(MessageCountEvent e) {

            }
        };

        // Add the handler for messages to be added in the future.
        folder.addMessageCountListener(messageCountListener);

        idleThread = new Thread() {
            @Override
            public void run() {
                Logger.info("Start the Email Receiving Thread");
                while (true) {
                    if (isStopping) break;
                    try {
                        // Notify the message count listener if the value of EXISTS response is
                        // larger than realTotal.
                        folder.idle();
                    } catch (FolderClosedException e) {
                        if (isStopping) break;
                        // reconnect
                        Logger.info("Reopen the imap folder");
                        try {
                            folder = reopenFolder();
                        } catch (MessagingException e1) {
                            Logger.warn("Failed to reopen the imap folder; " +
                                    "abort", e1);
                            break;
                        }
                    } catch (Exception e) {
                        Logger.warn("Failed to run IDLE command; abort", e);
                        break;
                    }
                }
                Logger.info("Stop the Email Receiving Thread");
            }
        };
        idleThread.start();
    }

    /**
     * Update the lastSeenUID.
     *
     * lastSeenUID MUST be updated when a new email is processed so that
     * MailboxService fetches new emails correctly.
     *
     * @param msg
     * @throws MessagingException
     */
    synchronized static void updateLastSeenUID(IMAPMessage msg) throws MessagingException {
        long uid = folder.getUID(msg);

        // Do not update lastSeenUID if it is larger than the current uid.
        try {
            long lastSeenUID = Property.getLong(Property.Name.MAILBOX_LAST_SEEN_UID);
            if (uid <= lastSeenUID) {
                return;
            }
        } catch (Exception ignored) { }

        Property.set(Property.Name.MAILBOX_LAST_SEEN_UID, uid);
    }
}
