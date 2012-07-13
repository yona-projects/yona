package models.support;

import models.enumeration.Matching;

public class SearchParam {

    private String field;

    private Object value;

    private Matching matching;

    public SearchParam(String field, Object value, Matching matching) {
        this.field = field;
        this.value = value;
        this.matching = matching;
    }

    public String getField() {
        return field;
    }

    public Object getValue() {
        return value;
    }

    public Matching getMatching() {
        return matching;
    }
}
