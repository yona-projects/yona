/**
 * Yobi, Project Hosting SW
 *
 * Copyright 2013 NAVER Corp.
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

import static org.fest.assertions.Assertions.assertThat;

import controllers.AbstractPostingApp;
import models.enumeration.Direction;

import models.enumeration.Matching;
import models.support.FinderTemplate;
import models.support.OrderParams;
import models.support.SearchParams;
import org.junit.*;

import utils.JodaDateUtil;

import com.avaje.ebean.Page;

import com.avaje.ebean.Ebean;

import java.util.List;

public class PostingTest extends ModelTest<Posting> {

    @Test
    public void findById() throws Exception {
        // Given
        // When
        Posting actual = Posting.finder.byId(1l);
        // Then
        assertThat(actual).isNotNull();
        assertThat(actual.title).isEqualTo("게시판이 새로 생성되었습니다.");
        assertThat(actual.authorId).isEqualTo(2l);
    }

    @Test
    public void findOnePage() throws Exception {
        // Given
        // When
        SearchParams searchParam = new SearchParams()
            .add("project.owner", "yobi", Matching.EQUALS)
            .add("project.name", "projectYobi", Matching.EQUALS)
            .add("body", "", Matching.CONTAINS);
        OrderParams orderParams = new OrderParams().add("id", Direction.DESC);
        Page<Posting> page = FinderTemplate.getPage(orderParams, searchParam, Posting.finder, AbstractPostingApp.ITEMS_PER_PAGE, 0);
        // Then
        assertThat(page.getList()).hasSize(1);
    }

    @Test
    public void save() throws Exception {
        // Given
        Posting post = getNewPosting();

        // When
        post.save();

        // Then
        Posting actual = Posting.finder.byId(post.id);
        assertThat(post.id).isGreaterThan(0);
        assertThat(actual.title).isEqualTo(post.title);
        assertThat(actual.getBody()).isEqualTo(post.getBody());
        assertThat(actual.createdDate).isEqualTo(post.createdDate);
        assertThat(actual.authorId).isEqualTo(getTestUser().id);
        assertThat(actual.id).isEqualTo(post.id);

        // To keep data clean after this test.
        post.delete();
    }

    private Posting getNewPosting() {
        Posting post = new Posting();
        post.setBody("new Contents");
        post.title = "new_title";
        post.createdDate = JodaDateUtil.now();
        post.project = Project.find.byId(1l);
        post.setAuthor(getTestUser());
        return post;
    }

    @Test
    public void delete() throws Exception {
        // Given
        Posting post = getNewPosting();
        post.save();
        long postId = post.id;
        assertThat(Posting.finder.byId(postId)).isNotNull();

        // When
        Posting.finder.byId(postId).delete();

        // Then
        assertThat(Posting.finder.byId(postId)).isNull();
        assertThat(PostingComment.find.byId(postId)).isNull();
    }

    @Test
    public void update() throws Exception {
        // Given
        Posting post = Posting.finder.byId(1l);
        post.setBody("수정되었습니다.");
        post.id = 1l;

        // When
        post.update();

        // Then
        Posting actual = Posting.finder.byId(1l);
        assertThat(actual.getBody()).isEqualTo("수정되었습니다.");
        assertThat(actual.numOfComments).isEqualTo(1);
    }

    @Test
    public void author() throws Exception {
        // Given
        Long currentUserId_yobi = 2l;
        Long postId = 1l;
        // When
        boolean result = Posting.finder.byId(postId).asResource().getAuthorId().equals(currentUserId_yobi);
        // Then
        assertThat(result).isEqualTo(true);
    }
}
