package models;

import models.resource.Resource;

/**
 * 객체를 {@code Resource} 로 convert 하기 위한 인터페이스
 * 여러 클래스들을 {@code Resource} 라는 동일한 클래스로
 * 취급하기 위한 용도로 사용한다.
 * @see models.resource.Resource
 */
public interface ResourceConvertible {
    /**
     * convert current object to {@link Resource}
     * @return Resource
     */
    Resource asResource();
}
