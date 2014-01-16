/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keesun Baik
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

import actions.IsAllowedAction;
import models.enumeration.Operation;
import models.enumeration.ResourceType;
import play.mvc.With;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * /{user.loginId}/{project.name}/** 에 해당하는 URL에 적용할 수 있는 리소스 존재 확인 및 권한 확인용 애노테이션.
 * - URL 패턴에 대응하는 프로젝트가 있는 확인하다. 없으면 404 Not Found.
 * - {@code resourceType}에 해당하는 리소스가 있는지 확인한다. 없으면 404 Not Found.
 * - 현재 사용자가 {@code resourceType}에 해당하는 리소스에 {@code value}에 해당하는 권한이 있는지 확인한다. 권한이 없으면 403 Forbidden.
 *
 * 이 애노테이션으로 다룰 수 있는 리소스 타입은
 * {@link models.resource.Resource#getResourceObject(actions.support.PathParser, models.Project, models.enumeration.ResourceType)}
 * 참조하고 필요하면 추가 해야 한다.
 *
 * @author Keesun Baik
 * @see {@link actions.IsAllowedAction}
 * @see {@link models.resource.Resource#getResourceObject(actions.support.PathParser, models.Project, models.enumeration.ResourceType)}
 */
@With(IsAllowedAction.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface IsAllowed {
    Operation value();
    ResourceType resourceType() default ResourceType.PROJECT;
}
