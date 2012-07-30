package support;

import com.avaje.ebean.bean.PersistenceContext;
import play.db.ebean.Model;

public class EbeanUtil<T> {

    private Model.Finder<Long, T> find;


    public EbeanUtil(Class<T> type) {
        find = new Model.Finder<Long, T>(Long.class, type);
    }

    public void flush(T model) {
        Model _model = ((Model) model);
        if (_model._ebean_getIntercept() != null) {
            PersistenceContext pc = _model._ebean_getIntercept().getPersistenceContext();
            if (pc != null) {
                pc.clear();
            }
        }
    }

    public void flush(Long id) {
        this.flush(find.byId(id));
    }
}
