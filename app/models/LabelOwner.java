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
package models;

import java.util.Set;

import models.resource.ResourceConvertible;

/**
 * 라벨을 가지고 있는 객체에서 라벨 set 을 꺼내기 위한 인터페이스
 * 라벨의 클래스는 {@code ResourceConvertible} 을 구현해야 한다.
 * @see models.resource.ResourceConvertible
 */
public interface LabelOwner extends ResourceConvertible {
    /**
     * 라벨 set 을 꺼낸다.
     * @return 라벨 set
     */
    Set<? extends ResourceConvertible> getLabels();
}
