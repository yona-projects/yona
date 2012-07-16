package models.enumeration;

public enum Direction {

    ASC("asc"), DESC("desc");

    private String direction;

    Direction(String direction) {
        this.direction = direction;
    }

    public String direction() {
        return this.direction;
    }

    public static Direction getValue(String value) {
        for (Direction direction : Direction.values()) {
            if (direction.direction().equals(value)) {
                return direction;
            }
        }
        return Direction.ASC;
    }

}
