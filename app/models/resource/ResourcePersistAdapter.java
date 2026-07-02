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

import io.ebean.Database;
import io.ebean.Query;
import io.ebean.Transaction;
import io.ebean.event.BeanPersistAdapter;
import io.ebean.event.BeanPersistRequest;
import models.Unwatch;
import models.Watch;

/**
 * @see io.ebean.event.BeanPersistController
 * @see io.ebean.event.BeanPersistAdapter
 */
public class ResourcePersistAdapter extends BeanPersistAdapter {
    /**
     * @see io.ebean.event.BeanPersistAdapter#isRegisterFor(Class)
     */
    @Override
    public boolean isRegisterFor(Class<?> cls) {
        return ResourceConvertible.class.isAssignableFrom(cls);
    }

    /**
     * @see io.ebean.event.BeanPersistAdapter#postDelete(BeanPersistRequest)
     */
    @Override
    public void postDelete(BeanPersistRequest<?> request) {
        // deleted resource
        Resource resource = ((ResourceConvertible) request.bean()).asResource();
        Transaction transaction = request.transaction();
        Database server = request.database();

        // delete related objects
        deleteRelatedWatch(resource, server, transaction);
        deleteRelatedUnwatch(resource, server, transaction);
    }

    private void deleteRelatedWatch(Resource resource, Database server, Transaction transaction) {
        Query<Watch> query = server.createQuery(Watch.class);
        query.where().eq("resourceType", resource.getType()).eq("resourceId", resource.getId());
        server.deleteAll(Watch.class, query.findIds(), transaction);
    }

    private void deleteRelatedUnwatch(Resource resource, Database server, Transaction transaction) {
        Query<Unwatch> query = server.createQuery(Unwatch.class);
        query.where().eq("resourceType", resource.getType()).eq("resourceId", resource.getId());
        server.deleteAll(Unwatch.class, query.findIds(), transaction);
    }
}
