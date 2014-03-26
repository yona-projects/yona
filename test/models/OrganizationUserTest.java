/*
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
 * http://yobi.io
 *
 * @Author Changsung Kim
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

import controllers.routes;
import models.enumeration.RoleType;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import play.test.Helpers;
import play.test.FakeApplication;
import static org.fest.assertions.Assertions.assertThat;
import static play.test.Helpers.callAction;

/**
 * Created by kcs on 2/21/14.
 */
public class OrganizationUserTest {
    protected static FakeApplication app;

    @BeforeClass
    public static void beforeClass() {
        callAction(
                routes.ref.Application.init()
        );
    }

    @Before
    public void before() {
        app = support.Helpers.makeTestApplication();
        Helpers.start(app);
    }

    private Organization createOrganization(String name) {
        Organization organization = new Organization();
        organization.name = name;
        organization.save();

        return organization;
    }

    @Test
    public void addMember() {
        // Given
        Organization organization = createOrganization("TestOrganization");
        User user = User.findByLoginId("laziel");

        // When
        OrganizationUser.assignRole(user.id, organization.id, RoleType.ORG_ADMIN.roleType());

        // Then
        OrganizationUser organizationUser = OrganizationUser.findByOrganizationIdAndUserId(organization.id, user.id);
        assertThat(organizationUser.user.id).isEqualTo(user.id);
    }

    @Test
    public void checkMemberInfo() {
        // Given
        Organization organization = createOrganization("TestOrganization");
        User user = User.findByLoginId("laziel");
        Long roleType = RoleType.ORG_ADMIN.roleType();

        // When
        OrganizationUser.assignRole(user.id, organization.id, roleType);
        OrganizationUser organizationUser = OrganizationUser.findByOrganizationIdAndUserId(organization.id, user.id);

        // Then
        assertThat(organizationUser.user.getDateString()).isNotNull();
        assertThat(organizationUser.user.loginId).isEqualTo(user.loginId);
        assertThat(organizationUser.organization.id).isEqualTo(organization.id);
        assertThat(organizationUser.role.id).isEqualTo(roleType);
    }

    @Test
    public void deleteMember() {
        // Given
        Organization organization = createOrganization("TestOrganization");
        User user = User.findByLoginId("laziel");
        Long roleType = RoleType.ORG_ADMIN.roleType();

        // When
        OrganizationUser.assignRole(user.id, organization.id, roleType);
        OrganizationUser.delete(organization.id, user.id);
        OrganizationUser organizationUser = OrganizationUser.findByOrganizationIdAndUserId(organization.id, user.id);

        // Then
        assertThat(organizationUser).isEqualTo(null);
    }

    @Test
    public void editMember() {
        // Given
        Organization organization = createOrganization("TestOrganization");
        User user = User.findByLoginId("laziel");
        Long firstRoleType = RoleType.ORG_ADMIN.roleType();
        Long secondRoleType = RoleType.ORG_MEMBER.roleType();

        // When & Then
        OrganizationUser.assignRole(user.id, organization.id, firstRoleType);
        OrganizationUser organizationUser = OrganizationUser.findByOrganizationIdAndUserId(organization.id, user.id);
        assertThat(organizationUser.role.id).isEqualTo(firstRoleType);

        OrganizationUser.assignRole(user.id, organization.id, secondRoleType);
        organizationUser = OrganizationUser.findByOrganizationIdAndUserId(organization.id, user.id);
        assertThat(organizationUser.role.id).isEqualTo(secondRoleType);
    }

}
