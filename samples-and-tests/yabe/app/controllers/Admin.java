package controllers;
 
import java.util.List;

import models.Post;
import models.User;
import play.data.validation.Validation;
import play.mvc.Before;
import play.mvc.Controller;
import play.mvc.With;

import com.google.code.morphia.emul.org.bson.types.ObjectId;
 
@With(Secure.class)
public class Admin extends Controller {
    
    @Before
    static void setConnectedUser() {
        if(Security.isConnected()) {
            User user = User.find("byEmail", Security.connected()).first();
            renderArgs.put("user", user.fullname);
        }
    }
 
    public static void index() {
        User author = User.filter("email", Security.connected()).first();
        List<Post> posts = Post.filter("author", author).asList();
        render(posts);
    }
    // Using String instead of ObjectId is needed as
    // Play unbind will cause StackOverflowError. 
    // See http://groups.google.com/group/play-framework/browse_thread/thread/8fa3cbd25b635ed3/542eee6d20a73d53?lnk=gst&q=unbind+stackOverflowError#542eee6d20a73d53
    public static void form(String id) {
        if(id != null) {
            Post post = Post.findById(new ObjectId(id));
            render(post);
        }
        render();
    }
    
    public static void save(String id, String title, String content, String tags) {
        Post post;
        if(id == null) {
            // Create post
            User author = User.find("byEmail", Security.connected()).first();
            post = new Post(author, title, content);
        } else {
            // Retrieve post
            post = Post.findById(new ObjectId(id));
            post.title = title;
            post.content = content;
            post.tags.clear();
        }
        // Set tags list
        for(String tag : tags.split("\\s+")) {
            if(tag.trim().length() > 0) {
                post.tagItWith(tag);
            }
        }
        // Validate
        validation.valid(post);
        if(Validation.hasErrors()) {
            render("@form", post);
        }
        // Save
        post.save();
        index();
    }
    
}
