/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Wansoon Park, Keesun Baek
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
package controllers.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import models.enumeration.ResourceType;
import play.mvc.With;
import actions.IsCreatableAction;

/**
 * /{user.loginId}/{project.name}/** 에 해당하는 URL에 적용할 수 있는 리소스 생성 권한 확인하는 애노테이션.
 * - URL 패턴에 대응하는 프로젝트가 있는 확인하다. 없으면 404 Not Found.
 * - 현재 사용자가 {@code resourceType}에 해당하는 리소스를 생성할 수 있는지 확인한다. 권한이 없으면 403 Forbidden.
 *
 * 이 애노테이션은 {@link utils.AccessControl#isProjectResourceCreatable(models.User, models.Project, models.enumeration.ResourceType)}
 * 을 사용한다.
 *
 * @author Wansoon Park, Keesun Baik
 * @see {@link actions.IsCreatableAction}
 * @see {@link utils.AccessControl#isProjectResourceCreatable(models.User, models.Project, models.enumeration.ResourceType)}
 */
@With(IsCreatableAction.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface IsCreatable {
    ResourceType value();
}
