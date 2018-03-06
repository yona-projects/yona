/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp. & NAVER LABS Corp.
 * https://yona.io
 **/

package utils;

public class Timestamp {

    private long lastCheckedTime;

    public Timestamp(String title) {
        this.lastCheckedTime =  System.currentTimeMillis();
        play.Logger.info(title);
    }

    public void logElapsedTime(String message) {
        long currentTimeMillis = System.currentTimeMillis();
        play.Logger.info(message + " - " + (currentTimeMillis - lastCheckedTime) + " ms");
        lastCheckedTime = currentTimeMillis;
    }
}
