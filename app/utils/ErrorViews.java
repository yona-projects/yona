/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Wansoon Park
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
package utils;
import controllers.UserApp;
import models.Organization;
import models.Project;
import models.User;
import play.twirl.api.Html;
import views.html.index.index;


/**
 * The Enum Views.
 */
public enum ErrorViews {
    Forbidden {
        @Override
        public Html render(String messageKey) {
            return views.html.error.forbidden_default.render(messageKey);
        }

        @Override
        public Html render(String messageKey, Project project) {
            return views.html.error.forbidden.render(messageKey, project);
        }

        public Html render(String messageKey, String returnUrl) {
            if (UserApp.currentUser() == User.anonymous) {
                return views.html.user.login.render("error.fobidden", null, returnUrl);
            } else {
                return views.html.error.forbidden_default.render(messageKey);
            }
        }

        @Override
        public Html render(String messageKey, Organization organization) {
            return views.html.error.forbidden_organization.render(messageKey, organization);
        }

        @Deprecated
        @Override
        public Html render(String messageKey, Project project, String type) {
            return null;
        }

        public Html render(String messageKey, Project project, MenuType menuType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Html render() {
            return render("error.forbidden");
        }
    },
    NotFound {
        @Override
        public Html render(String messageKey) {
            return views.html.error.notfound_default.render(messageKey);
        }

        @Override
        public Html render(String messageKey, Project project) {
            return render(messageKey, project, MenuType.PROJECT_HOME);
        }

        @Override
        public Html render(String messageKey, Organization organization) {
            // TODO : make notfound view for organization
            return views.html.error.notfound_default.render(messageKey);
        }

        @Override
        public Html render(String messageKey, Project project, String type) {
            return views.html.error.notfound.render(messageKey, project, type);
        }

        public Html render(String messageKey, Project project, MenuType menuType) {
            return views.html.error.notfound_default.render(messageKey);
        }

        @Override
        public Html render() {
            return render("error.notfound");
        }
    },
    RequestTextEntityTooLarge {
        @Override
        public Html render() {
            return views.html.error.requestTextEntityTooLarge.render();
        }

        @Override
        public Html render(String messageKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Html render(String messageKey, Project project) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Html render(String messageKey, Organization organization) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Html render(String messageKey, Project project, String target) {
            throw new UnsupportedOperationException();
        }
        public Html render(String messageKey, Project project, MenuType menuType) {
            throw new UnsupportedOperationException();
        }
    },
    BadRequest {
        @Override
        public Html render(String messageKey) {
            return views.html.error.badrequest_default.render(messageKey);
        }

        @Override
        public Html render(String messageKey, Project project) {
            return views.html.error.badrequest.render(messageKey, project, MenuType.PROJECT_HOME);
        }

        @Override
        public Html render(String messageKey, Organization organization) {
            // TODO : make badrequest view for organization
            return views.html.error.badrequest_default.render(messageKey);
        }

        @Deprecated
        @Override
        public Html render(String messageKey, Project project, String type) {
            return null;
        }

        public Html render(String messageKey, Project project, MenuType menuType) {
            return views.html.error.badrequest.render(messageKey, project, menuType);
        }

        @Override
        public Html render() {
            return render("error.badrequest");
        }

    };

    public abstract Html render();

    public abstract Html render(String messageKey);

    public abstract Html render(String messageKey, Project project);

    public abstract Html render(String messageKey, Organization organization);

    public abstract Html render(String messageKey, Project project, String target);

    public abstract Html render(String messageKey, Project project, MenuType menuType);

    public Html render(String messageKey, String returnUrl) {
        return index.render(UserApp.currentUser());
    };
}
