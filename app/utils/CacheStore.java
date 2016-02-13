package utils;

import models.Project;
import models.User;

import java.util.HashMap;
import java.util.Map;

/**
 * CacheStore
 */
public class CacheStore {
    public static Map<String, User> sessionMap = new HashMap<>();
    public static Map<String, Project> projectMap = new HashMap<>();
}
