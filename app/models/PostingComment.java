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
package models;

import models.enumeration.ResourceType;
import models.resource.Resource;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

@Entity
public class PostingComment extends Comment {
    private static final long serialVersionUID = 1L;
    public static final Finder<Long, PostingComment> find = new Finder<>(Long.class, PostingComment.class);

    @ManyToOne
    public Posting posting;

    public PostingComment(Posting posting, User author, String contents) {
        super(author, contents);
        this.posting = posting;
    }

    /**
     * @see Comment#getParent()
     */
    public AbstractPosting getParent() {
        return posting;
    }

    /**
     * @see Comment#asResource()
     */
    @Override
    public Resource asResource() {
        return new Resource() {
            @Override
            public String getId() {
                return id.toString();
            }

            @Override
            public Project getProject() {
                return posting.project;
            }

            @Override
            public ResourceType getType() {
                return ResourceType.NONISSUE_COMMENT;
            }

            @Override
            public Long getAuthorId() {
                return authorId;
            }

            @Override
            public Resource getContainer() {
                return posting.asResource();
            }
        };
    }
}
