package models.support;

import models.enumeration.*;

import java.util.*;

public class SearchParams {

    private List<SearchParam> searchParams;

    public SearchParams() {
        this.searchParams = new ArrayList<>();
    }

    public SearchParams add(String field, Object value, Matching matching) {
        this.searchParams.add(new SearchParam(field, value, matching));
        return this;
    }

    public List<SearchParam> getSearchParams() {
        return searchParams;
    }

    public List<SearchParam> clean() {
        this.searchParams.clear();
        return this.searchParams;
    }
}
