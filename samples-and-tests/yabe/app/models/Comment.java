package models;
 
import java.util.Date;

import play.data.validation.MaxSize;
import play.data.validation.Required;
import play.modules.morphia.Model;
import play.modules.morphia.utils.IdGenerator;

import com.google.code.morphia.annotations.Embedded;
import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Reference;
 
@Embedded
@Entity
public class Comment extends Model {
 
    @Required
    public String author;
    
    @Required
    public Date postedAt;
     
    //@Lob
    @Required
    @MaxSize(10000)
    public String content;
    
    //@ManyToOne
    @Required
    @Reference
    public Post post;
    
    public Comment(Post post, String author, String content) {
        this.post = post;
        this.author = author;
        this.content = content;
        this.postedAt = new Date();
        
        post.addComment(this);
    }
    
    public String toString() {
        return content.length() > 50 ? content.substring(0, 50) + "..." : content;
    }
    
}