/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Jungkook Kim
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package models.resource;

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
