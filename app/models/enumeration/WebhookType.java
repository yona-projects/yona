/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/
package models.enumeration;

public enum WebhookType {
    SIMPLE(0), WITH_DETAILS(1);

    private int type;

    WebhookType(int type) {
        this.type = type;
    }
}
