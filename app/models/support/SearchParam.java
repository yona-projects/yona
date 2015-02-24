/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @author Yoon
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
package models.support;

import models.enumeration.Matching;

public class SearchParam {

    private String field;

    private Object value;

    private Matching matching;

    public SearchParam(String field, Object value, Matching matching) {
        this.field = field;
        this.value = value;
        this.matching = matching;
    }

    public String getField() {
        return field;
    }

    public Object getValue() {
        return value;
    }

    public Matching getMatching() {
        return matching;
    }
}
