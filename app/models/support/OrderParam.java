package models.support;

import models.enumeration.Ordering;

public class OrderParam {

    private String field;

    private Ordering ordering;

    public OrderParam(String field, Ordering ordering) {
        this.field = field;
        this.ordering = ordering;
    }

    public String getField() {
        return field;
    }

    public Ordering getOrdering() {
        return ordering;
    }
}
