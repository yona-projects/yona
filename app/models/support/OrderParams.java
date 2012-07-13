package models.support;

import models.enumeration.Ordering;

import java.util.ArrayList;
import java.util.List;

public class OrderParams {

    private List<OrderParam> orderParams;

    public OrderParams() {
        this.orderParams = new ArrayList<OrderParam>();
    }

    public OrderParams add(String field, Ordering ordering) {
        this.orderParams.add(new OrderParam(field, ordering));
        return this;
    }

    public List<OrderParam> getOrderParams() {
        return orderParams;
    }

    public List<OrderParam> clean() {
        this.orderParams.clear();
        return this.orderParams;
    }
}
