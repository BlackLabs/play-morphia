package models;
 
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.modules.morphia.Model;

import com.google.code.morphia.annotations.Embedded;
import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Reference;

@Entity(value="pt", noClassnameStored=true)
public class Post extends Model {
 
    @Required
    @com.google.code.morphia.annotations.Property(value="ttl")
    public String title;
    
    @Required @As("yyyy-MM-dd")
    public Date postedAt;
    
    //@Lob
    @Required
    @MaxSize(10000)
    @com.google.code.morphia.annotations.Property(value="ctnt")
    public String content;
    
    @Required
    //@ManyToOne
    @Reference
    public User author;
    
    //@OneToMany(mappedBy="post", cascade=CascadeType.ALL)
    @Embedded
    public List<Comment> comments;
    
    //@ManyToMany(cascade=CascadeType.PERSIST)
    @Embedded
    public Set<String> tags;
    
    public Post(User author, String title, String content) { 
        this.comments = new ArrayList<Comment>();
        this.tags = new TreeSet();  
        this.author = author;
        this.title = title;
        this.content = content;
        this.postedAt = new Date();
    }
    
    public Post addComment(String author, String content) {
        Comment newComment = new Comment(this, author, content);
        //this.comments.add(newComment); -- comments has already added in Comment constructor
        this.save();
        return this;
    }
    
    public List<Comment> getComments() {
        if (null == comments) {
            comments = new ArrayList<Comment>();
        }
        return comments;
    }
    
    void addComment(Comment comment) {
        if (null == comments) {
            comments = new ArrayList<Comment>();
        }
        comments.add(comment);
    }
    
    public Post previous() {
        return (Post) Post.filter("postedAt <", postedAt).order("-postedAt").get();
    }

    public Post next() {
        return (Post) Post.filter("postedAt >", postedAt).order("postedAt").get();
    }
    
    public Post tagItWith(String name) {
        tags.add(name);
        return this;
    }
    
    public static List<Post> findTaggedWith(String tag) {
        return Post.filter("tags", tag).asList();
    }
    
    public static List<Post> findTaggedWith(String... tags) {
        return Post.filter("tags in", tags).asList();
    }
    
    public String toString() {
        return title == null ? super.toString() : title;
    }
 
}
