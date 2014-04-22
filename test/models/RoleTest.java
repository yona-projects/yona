/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2012 NAVER Corp.
 * http://yobi.io
 *
 * @Author Hwi Ahn
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

import java.util.List;

import models.enumeration.RoleType;

import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public class RoleTest extends ModelTest<Role> {
    @Test
    public void findById() throws Exception {
        // Given
        // When
        Role role = Role.findByRoleType(RoleType.MANAGER);
        // Then
        assertThat(role.name).isEqualTo("manager");
    }

    @Test
    public void findByName() throws Exception {
        // Given
        // When
        Role role = Role.findByName("manager");
        // Then
        assertThat(role.id).isEqualTo(1l);
    }

    @Test
    public void getAllProjectRoles() throws Exception {
        // Given
        // When
        List<Role> roles = Role.findProjectRoles();
        // Then
        assertThat(roles.contains(Role.findByName("siteManager"))).isEqualTo(false);
        assertThat(roles.contains(Role.findByName("manager"))).isEqualTo(true);
        assertThat(roles.contains(Role.findByName("member"))).isEqualTo(true);
    }

    @Test
    public void findRoleByIds() throws Exception {
        // Given
        // When
        Role role = Role.findRoleByIds(2l, 1l);

        // Then
        assertThat(role).isNotNull();
    }
}
