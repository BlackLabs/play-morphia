package models;
 
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.persistence.Lob;

import play.data.binding.As;
import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.modules.morphia.Model;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Reference;

@Entity
public class Post extends Model {
 
    @Required
    public String title;
    
    @Required @As("yyyy-MM-dd")
    public Date postedAt;
    
    @Lob
    @Required
    @MaxSize(10000)
    public String content;
    
    @Required
    public String authorEmail;
    
    @Reference
    public List<Comment> comments;
    
    public Set<String> tags = new HashSet<String>();
    
    public Post(User author, String title, String content) { 
        this.comments = new ArrayList<Comment>();
        this.tags = new TreeSet();  
        this.authorEmail = author.email;
        this.title = title;
        this.content = content;
        this.postedAt = new Date();
    }
    
    public User getAuthor() {
        return User.q("email", authorEmail).get();
    }
    
    public Post addComment(String author, String content) {
        /*
        Comment newComment = new Comment(this, author, content).save();
        this.comments.add(newComment);
        this.save();
         */
        new Comment(this, author, content).save();
        return this;
    }
    
    public Post previous() {
        return Post.q().filter("postedAt <", postedAt).order("-postedAt").first();
    }

    public Post next() {
        return Post.q().filter("postedAt >", postedAt).order("postedAt").first();
    }
    
    public Post tagItWith(String name) {
        tags.add(name);
        return this;
    }
    
    public static List<Post> findTaggedWith(String tag) {
//        return Post.find(
//            "select distinct p from Post p join p.tags as t where t.name = ?",
//            tag
//        ).fetch();
        return Post.q().filter("tags", tag).asList();
    }
    
    public static List<Post> findTaggedWith(String... tags) {
//        return Post.find(
//            "select distinct p.id from Post p join p.tags as t where t.name in (:tags) group by p.id having count(t.id) = :size"
//        ).bind("tags", tags).bind("size", tags.length).fetch();
        return Post.q().filter("tags all", tags).asList();
    }
    
    public String toString() {
        return title;
    }
    
    @OnDelete void cascadeDelete() {
        for (Comment c: comments) {
            c.delete();
        }
    }
 
}
