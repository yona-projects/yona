/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
package models.enumeration;

public enum WebhookType {
    SIMPLE(0), DETAIL_SLACK(1), DETAIL_HANGOUT_CHAT(2);

    private int type;

    WebhookType(int type) {
        this.type = type;
    }
}
