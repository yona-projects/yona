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

import actions.IsOnlyGitAvailableAction;
import play.mvc.With;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * /{user.loginId}/{project.name}/** 에 해당하는 URL에 적용할 수 있는 Git 프로젝트 확인용 애노테이션.
 * - URL 패턴에 대응하는 프로젝트가 있는 확인하다. 없으면 404 Not Found.
 * - 접근하려는 프로젝트가 Git 프로젝트인지 확인한다. Git 프로젝트가 아니면 400 BadRequest.
 *
 * @author Keesun Baik
 * @see {@link actions.IsOnlyGitAvailableAction}
 */
@With(IsOnlyGitAvailableAction.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface IsOnlyGitAvailable {

}
