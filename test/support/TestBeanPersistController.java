package support;

import com.avaje.ebean.bean.PersistenceContext;
import com.avaje.ebean.event.BeanPersistController;
import com.avaje.ebean.event.BeanPersistRequest;
import play.db.ebean.Model;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: Yoon
 * Date: 7/29/12
 * Time: 3:18 AM
 */
public class TestBeanPersistController implements BeanPersistController {

    @Override
    public int getExecutionOrder() {
        return 0;
    }

    @Override
    public boolean isRegisterFor(Class<?> cls) {
        return true;
    }

    @Override
    public boolean preInsert(BeanPersistRequest<?> request) {
        return true;
    }

    @Override
    public boolean preUpdate(BeanPersistRequest<?> request) {
        return true;
    }

    @Override
    public boolean preDelete(BeanPersistRequest<?> request) {
        return true;
    }

    @Override
    public void postInsert(BeanPersistRequest<?> request) {

    }

    @Override
    public void postUpdate(BeanPersistRequest<?> request) {
        cleanPersistenceContext(request.getBean());
    }

    @Override
    public void postDelete(BeanPersistRequest<?> request) {
        cleanPersistenceContext(request.getBean());
    }

    @Override
    public void postLoad(Object bean, Set<String> includedProperties) {

    }

    private void cleanPersistenceContext(Object bean) {
        Model _model = ((Model) bean);
        if (_model != null && _model._ebean_getIntercept() != null) {
            PersistenceContext pc = _model._ebean_getIntercept().getPersistenceContext();
            if (pc != null) {
                pc.clear();
            }
        }

    }
}
