/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2015 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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
package notification;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MergedNotificationMail implements INotificationMail {
    private final List<INotificationMail> mails;
    private final INotificationMail main;

    public MergedNotificationMail(@Nonnull INotificationMail main,
                                  @Nonnull List<INotificationMail> mails) {
        this.main = main;
        this.mails = new ArrayList<>();
        this.mails.addAll(mails);
    }

    public MergedNotificationMail(@Nonnull INotificationMail main) {
        this(main, Arrays.asList(main));
    }

    @Override
    public INotificationEvent getEvent() {
        List<INotificationEvent> events = new ArrayList<>();
        for(INotificationMail mail : mails) {
            events.add(mail.getEvent());
        }
        return new MergedNotificationEvent(main.getEvent(), events);
    }

    @Override
    public void delete() {
        for(INotificationMail mail : mails) {
            mail.delete();
        }
    }

    public void merge(INotificationMail mail) {
        mails.add(mail);
    }
}
