/**
 * Yona, 21st Century Project Hosting SW
 * <p>
 * Copyright Yona & Yobi Authors & NAVER Corp.
 * https://yona.io
 **/
package utils;

import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.Map;

public class PathVariable {
    String url;
    public static final String rootPath = play.Configuration.root().getString("application.context", "");
    public static final String API_PREFIX = "/-_-api/v1/";
    private Map<String, String> pathVariable = new HashMap<>();
    private boolean isApiPathCall = false;

    public PathVariable(String url) {
        String refinedUrl  = url;
        if(StringUtils.isNotEmpty(rootPath)){
            refinedUrl = refinedUrl.replaceFirst(rootPath, "");
        }
        if(refinedUrl.startsWith(API_PREFIX)){
            refinedUrl = refinedUrl.replaceFirst(API_PREFIX, "");
            this.isApiPathCall = true;
            decomposeToPathVariable(refinedUrl);
        }
    }

    /**
     * ex> if url path is /-_-api/v1/owners/doortts/projects/test/issue/21
     * getPathVariable("owners") returns "doortts"
     * getPathVariable("projects") returns "test"
     */
    public String getPathVariable(String pathName) {
        return this.pathVariable.get(pathName);
    }

    public boolean isApiCall() {
        return this.isApiPathCall;
    }

    private void decomposeToPathVariable(String refinedUrl) {
        String[] decomposed = refinedUrl.split("/");
        for (int i = 0; i < decomposed.length; i = i + 2) {
            if (i + 1 > decomposed.length - 1) {
                pathVariable.put(decomposed[i], "");
            } else {
                pathVariable.put(decomposed[i], decomposed[i + 1]);
            }
        }
    }
}
