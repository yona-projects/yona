/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author Yi EungJun
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

import models.enumeration.ResourceType;
import play.libs.F;
import play.mvc.QueryStringBindable;

import java.util.Map;

public class ResourceParam implements QueryStringBindable<ResourceParam> {

    public Resource resource;

    public static ResourceParam get(Resource resource) {
        ResourceParam resourceParam = new ResourceParam();
        resourceParam.resource = resource;
        return resourceParam;
    }

    @Override
    public F.Option<ResourceParam> bind(String key, Map<String, String[]> data) {
        String type = data.get(key + ".type")[0];
        String id = data.get(key + ".id")[0];
        Resource result = Resource.get(ResourceType.getValue(type), id);
        if (result != null) {
            return F.Some(ResourceParam.get(result));
        } else {
            return new F.None<>();
        }
    }

    @Override
    public String unbind(String key) {
        return String.format("%s.type=%s&%s.id=%s",
                key, resource.getType().resource(), key, resource.getId());
    }

    @Override
    public String javascriptUnbind() {
        return "function(k,v) { return encodeURIComponent(k+'.type')+'='+v" +
                ".resource.type+'&'+encodeURIComponent(k+'.id')+'='+v.resource.id; }";
    }
}
