package mailbox;

import org.apache.commons.lang3.StringUtils;

import javax.mail.internet.MimePart;
import java.util.ArrayList;
import java.util.List;

public class Content {
    public String body = "";
    public final List<MimePart> attachments = new ArrayList<>();
    public String type;

    public Content() { }

    public Content(MimePart attachment) {
        this.attachments.add(attachment);
    }

    public Content merge(Content that) {
        body += that.body;
        if (StringUtils.isEmpty(type)) {
            type = that.type;
        }
        attachments.addAll(that.attachments);

        return this;
    }
}
