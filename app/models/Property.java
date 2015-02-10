/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2014 NAVER Corp.
 * http://yobi.io
 *
 * @Author Yi EungJun
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
package models;

import play.data.validation.Constraints;
import play.db.ebean.Model;
import utils.Diagnostic;

import javax.annotation.Nonnull;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Entity
public class Property extends Model {
    public static final Finder<Long, Property> find = new Finder<>(Long.class, Property.class);

    private static final long serialVersionUID = 8074682539173273921L;

    @Id
    public Long id;

    @Enumerated(EnumType.STRING)
    @Constraints.MaxLength(255)
    public Name name;

    @Constraints.MaxLength(4000)
    public String value;

    public static String get(Name name) {
        List<Property> properties = find.where().eq("name", name).findList();

        if (properties.size() > 0) {
            return properties.get(0).value;
        } else {
            return null;
        }
    }

    public static Long getLong(Name name) {
        String value = get(name);

        return value == null ? null : Long.valueOf(value);
    }

    public static void set(Name name, String value) {
        Property property = find.where().eq("name", name).findUnique();

        if (property == null) {
            property = new Property();
            property.name = name;
        }

        property.value = value;
        property.save();
    }

    public static void set(Name name, Long value) {
        set(name, value.toString());
    }

    public static enum Name {
        // the uid of the most recent email Mailbox has received
        MAILBOX_LAST_SEEN_UID,
        // the uidvalidity of the imap folder Mailbox has used most recently
        MAILBOX_LAST_UID_VALIDITY
        // Add property you need here.
    }

    public static void onStart() {
        Diagnostic.register(new Diagnostic() {
            @Override
            public @Nonnull List<String> check() {
                List<String> errors = new ArrayList<>();

                for (Property.Name name : Property.Name.values()) {
                    Set<Property> properties = Property.find.where().eq("name", name).findSet();
                    if (properties.size() > 1) {
                        errors.add(String.format("Property '%s' has duplicated values: %s",
                                name, properties));
                    }
                }

                return errors;
            }
        });
    }
}
