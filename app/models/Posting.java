/**
 * @author Ahn Hyeok Jun
 */

package models;

import javax.persistence.*;

import models.enumeration.ResourceType;
import models.resource.Resource;

import java.util.*;

@Entity
public class Posting extends AbstractPosting {
    private static final long serialVersionUID = 5287703642071155249L;

    public static Finder<Long, Posting> finder = new Finder<Long, Posting>(Long.class, Posting.class);

    @OneToMany(cascade = CascadeType.ALL)
    public List<PostingComment> comments;

    public int computeNumOfComments() {
        return comments.size();
    }

    public Posting() {
        super();
    }

    public Resource asResource() {
        return asResource(ResourceType.BOARD_POST);
    }
}
