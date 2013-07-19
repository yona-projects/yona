package utils;

import controllers.UserApp;
import play.i18n.Messages;
import play.mvc.Action;
import play.mvc.Http.Context;
import play.mvc.Result;

/**
 * The Class SiteManagerAuthAction.
 */
public class SiteManagerAuthAction extends Action.Simple {
    /**
     * 사이트 관리자 권한을 확인한다.
     * 
     * when : 사이트 관리자 페이지 접근시 사용
     * 
     * 관리자 권한이 아닐경우 경고메세지와 함께 {@link play.mvc.Results#forbidden} 을 반환한다.
     * 관리자 권한일 경우 기존 요청을 그대로 처리한다. 
     * 
     * @param context
     * @return
     * @throws Throwable
     * @see play.mvc.Action#call(play.mvc.Http.Context)
     * @see controllers.SiteApp
     */
    @Override
    public Result call(Context context) throws Throwable {
        if (!UserApp.currentUser().isSiteManager()) {
            return forbidden(Views.Forbidden.render("auth.unauthorized.waringMessage"));
        }
        return delegate.call(context);
    }

}
