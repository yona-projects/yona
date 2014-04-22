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

import models.Unwatch;
import models.Watch;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.Query;
import com.avaje.ebean.Transaction;
import com.avaje.ebean.event.BeanPersistAdapter;
import com.avaje.ebean.event.BeanPersistRequest;

/**
 * {@link Resource} 객체 이벤트 핸들러<br>
 * {@link ResourceConvertible} 을 구현한 객체에 변화가 있을때
 * 해당 객체를 {@link Resource} 로 변환하여 특정 작업을 수행한다. <br>
 *
 * @see com.avaje.ebean.event.BeanPersistController
 * @see com.avaje.ebean.event.BeanPersistAdapter
 */
public class ResourcePersistAdapter extends BeanPersistAdapter {
    /**
     * @see com.avaje.ebean.event.BeanPersistAdapter#isRegisterFor(Class)
     */
    @Override
    public boolean isRegisterFor(Class<?> cls) {
        return ResourceConvertible.class.isAssignableFrom(cls);
    }

    /**
     * {@link Resource} 가 삭제 되었을때 후처리
     * <ol>
     * <li>관련된 watch 정보를 삭제한다</li>
     * <li>관련된 unwatch 정보를 삭제한다</li>
     * </ol>
     * @see com.avaje.ebean.event.BeanPersistAdapter#postDelete(BeanPersistRequest)
     */
    @Override
    public void postDelete(BeanPersistRequest<?> request) {
        // deleted resource
        Resource resource = ((ResourceConvertible) request.getBean()).asResource();
        Transaction transaction = request.getTransaction();
        EbeanServer server = request.getEbeanServer();

        // delete related objects
        deleteRelatedWatch(resource, server, transaction);
        deleteRelatedUnwatch(resource, server, transaction);
    }

    private void deleteRelatedWatch(Resource resource, EbeanServer server, Transaction transaction) {
        Query<Watch> query = server.createQuery(Watch.class);
        query.where().eq("resourceType", resource.getType()).eq("resourceId", resource.getId());
        server.delete(Watch.class, query.findIds(), transaction);
    }

    private void deleteRelatedUnwatch(Resource resource, EbeanServer server, Transaction transaction) {
        Query<Unwatch> query = server.createQuery(Unwatch.class);
        query.where().eq("resourceType", resource.getType()).eq("resourceId", resource.getId());
        server.delete(Unwatch.class, query.findIds(), transaction);
    }
}
