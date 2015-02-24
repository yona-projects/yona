/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Jungkook Kim
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

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.Query;
import com.avaje.ebean.Transaction;
import com.avaje.ebean.event.BeanPersistAdapter;
import com.avaje.ebean.event.BeanPersistRequest;
import models.Unwatch;
import models.Watch;

/**
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
