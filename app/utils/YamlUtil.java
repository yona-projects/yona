/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @author kjkmadness
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

import java.util.List;
import java.util.Map;
import java.io.InputStream;

import io.ebean.Ebean;
import org.yaml.snakeyaml.Yaml;

public class YamlUtil {
    public static void insertDataFromYaml(String yamlFileName, String[] entityNames) {
        @SuppressWarnings("unchecked")
        Map<String, List<Object>> all = (Map<String, List<Object>>) load(yamlFileName);

        // Check whether every entities exist.
        for (String entityName : entityNames) {
            if (all.get(entityName) == null) {
                throw new RuntimeException("Failed to find the '" + entityName
                        + "' entity in '" + yamlFileName + "'");
            }
        }

        for (String entityName : entityNames) {
            Ebean.save(all.get(entityName));
        }
    }

    private static Object load(String yamlFileName) {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(yamlFileName);
        if (inputStream == null) {
            inputStream = YamlUtil.class.getClassLoader().getResourceAsStream(yamlFileName);
        }

        if (inputStream == null) {
            throw new RuntimeException("Failed to find the '" + yamlFileName + "' resource");
        }

        return new Yaml().load(inputStream);
    }
}
