package models;

import java.util.Set;

/**
 * 라벨을 가지고 있는 객체에서 라벨 set 을 꺼내기 위한 인터페이스
 * 라벨의 클래스는 {@code ResourceConvertible} 을 구현해야 한다.
 * @see models.ResourceConvertible
 */
public interface LabelOwner extends ResourceConvertible {
    /**
     * 라벨 set 을 꺼낸다.
     * @return 라벨 set
     */
    Set<? extends ResourceConvertible> getLabels();
}
