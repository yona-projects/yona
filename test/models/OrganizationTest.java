/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Keesun Baik
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

import org.junit.Before;
import org.junit.Test;
import play.data.validation.Validation;

import java.io.IOException;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;

/**
 * @author Keeun Baik
 */
public class OrganizationTest extends ModelTest<Organization> {

    /**
     * 조직을 생성하면 생성한 사용자가 조직의 org_admin 권한을 가진다.
     */
    @Test
    public void create() {
        // Given
        User doortts = User.findByLoginId("doortts");
        assertThat(doortts).isNotNull();

        Organization weblabs = new Organization();
        weblabs.name = "weblabs";
        weblabs.descr = "weblab < labs";
        weblabs.save();

        // When
        doortts.createOrganization(weblabs);

        // Then
        assertThat(Organization.findByName("weblabs")).isNotNull();
        List<OrganizationUser> ous = OrganizationUser.findAdminsOf(weblabs);
        assertThat(ous.size()).isEqualTo(1);
        assertThat(ous.get(0).user).isEqualTo(doortts);
    }

    /**
     * 조직의 이름은 사용자 이름과 동일한 패턴을 사용한다.
     */
    @Test
    public void validateName() {
        Organization org = new Organization();
        org.name="foo";
        assertThat(Validation.getValidator().validate(org).size()).describedAs("'foo' should be accepted.").isEqualTo(0);

        org.name=".foo";
        assertThat(Validation.getValidator().validate(org).size()).describedAs("'.foo' should NOT be accepted.").isGreaterThan(0);

        org.name="foo.bar";
        assertThat(Validation.getValidator().validate(org).size()).describedAs("'foo.bar' should be accepted.").isEqualTo(0);

        org.name="foo.";
        assertThat(Validation.getValidator().validate(org).size()).describedAs("'foo.' should NOT be accepted.").isGreaterThan(0);

        org.name="_foo";
        assertThat(Validation.getValidator().validate(org).size()).describedAs("'_foo' should NOT be accepted.").isGreaterThan(0);

        org.name="foo_bar";
        assertThat(Validation.getValidator().validate(org).size()).describedAs("'foo_bar' should be accepted.").isEqualTo(0);

        org.name="foo_";
        assertThat(Validation.getValidator().validate(org).size()).describedAs("'foo_' should NOT be accepted.").isGreaterThan(0);

        org.name="-foo";
        assertThat(Validation.getValidator().validate(org).size()).describedAs("'-foo' should be accepted.").isEqualTo(0);

        org.name="foo-";
        assertThat(Validation.getValidator().validate(org).size()).describedAs("'foo-' should be accepted.").isEqualTo(0);

        org.name="foo bar";
        assertThat(Validation.getValidator().validate(org).size()).describedAs("'foo bar' should NOT be accepted.").isGreaterThan(0);
    }

}
