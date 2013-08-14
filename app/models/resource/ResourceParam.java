package models.resource;

import models.enumeration.ResourceType;
import play.libs.F;
import play.mvc.QueryStringBindable;

import java.util.Map;

/**
* Created with IntelliJ IDEA.
* User: nori
* Date: 13. 8. 13
* Time: 오전 11:00
* To change this template use File | Settings | File Templates.
*/
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