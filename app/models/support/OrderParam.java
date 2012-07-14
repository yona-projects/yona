package models.support;

import models.enumeration.Direction;

public class OrderParam {

    private String sort;

    private Direction direction;

    public OrderParam(String sort, Direction direction) {
        this.sort = sort;
        this.direction = direction;
    }

    public String getSort() {
        return sort;
    }

    public Direction getDirection() {
        return direction;
    }
}
