package io.ebean;

import java.util.Collection;

/**
 * Compatibility facade for the old Ebean static API used by Yona.
 */
public final class Ebean {
    private static volatile Database database;

    private Ebean() {
    }

    public static void use(Database database) {
        Ebean.database = database;
    }

    private static Database database() {
        Database configured = database;
        return configured != null ? configured : DB.getDefault();
    }

    public static <T> Query<T> find(Class<T> beanType) {
        return database().find(beanType);
    }

    public static <T> UpdateQuery<T> update(Class<T> beanType) {
        return database().update(beanType);
    }

    public static SqlQuery createSqlQuery(String sql) {
        return database().sqlQuery(sql);
    }

    public static <T> Filter<T> filter(Class<T> beanType) {
        return database().filter(beanType);
    }

    public static void save(Object bean) {
        database().save(bean);
    }

    public static int save(Collection<?> beans) {
        return database().saveAll(beans);
    }

    public static void update(Object bean) {
        database().update(bean);
    }

    public static boolean delete(Object bean) {
        return database().delete(bean);
    }

    public static int delete(Class<?> beanType, Object id) {
        return database().delete(beanType, id);
    }
}
