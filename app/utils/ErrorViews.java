package utils;
import models.Project;
import play.api.templates.Html;


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
        
        @Deprecated
        @Override
        public Html render(String messageKey, Project project, String type) {
            return null;
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
            return render(messageKey, project, null);
        }
        
        @Override
        public Html render(String messageKey, Project project, String type) {
            return views.html.error.notfound.render(messageKey, project, type);
        }

        @Override
        public Html render() {
            return render("error.notfound");
        }
    },
    BadRequest {
        @Override
        public Html render(String messageKey) {
            return views.html.error.badrequest_default.render(messageKey);
        }

        @Override
        public Html render(String messageKey, Project project) {
            return views.html.error.badrequest.render(messageKey, project);
        }
        
        @Deprecated
        @Override
        public Html render(String messageKey, Project project, String type) {
            return null;
        }

        @Override
        public Html render() {
            return render("error.badrequest");
        }

    };
    
    /**
     * 오류페이지 HTML을 레더링 한다.
     * 오류타입에 따라 default messageKey를 사용하고 레이아웃은 사이트레벨이 된다.
     * 
     * notfound : error.notfound
     * fobidden : error.forbidden
     * badrequest : error.badrequest
     * 
     * @return
     */
    public abstract Html render();
    
    /**
     * 오류페이지 HTML을 레더링 한다.
     * 메세지는 파라미터로 전달되는 messageKey를 사용하고 레이아웃은 사이트레벨이 된다.
     * 
     * @param messageKey 메세지키
     * @return
     */
    public abstract Html render(String messageKey);
    
    /**
     * 오류페이지 HTML을 레더링 한다.
     * 메세지는 파라미터로 전달되는 messageKey를 사용하고 레이아웃은 프로젝트레벨이 된다.
     *   
     * @param messageKey 메세지키
     * @param project 프로젝트 정보
     * @return
     */
    public abstract Html render(String messageKey, Project project);
    
    /**
     * 오류페이지 HTML을 레더링 한다.
     * 메세지와 레이아웃은 세부타겟정보에 따라 이슈/게시판/프로젝트로 나뉘어 진다. 
     *  
     * @param messageKey 메세지키
     * @param project 프로젝트 정보
     * @param type 세부타겟 issue/post/etc(null, ... )
     * @return
     */
    public abstract Html render(String messageKey, Project project, String target);
    
}
