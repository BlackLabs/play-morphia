package controllers;

import play.*;
import play.mvc.*;
import play.data.validation.*;
import play.libs.*;
import play.cache.*;
 
import java.util.*;

import models.*;
 
public class Application extends Controller {
    
    @Before
    static void addDefaults() {
        renderArgs.put("blogTitle", Play.configuration.getProperty("blog.title"));
        renderArgs.put("blogBaseline", Play.configuration.getProperty("blog.baseline"));
    }
 
    public static void index() {
        Post frontPost = Post.find().order("-postedAt").first();
        List<Post> olderPosts = Post.find().order("-postedAt").offset(1).limit(10).asList();
        render(frontPost, olderPosts);
    }
    
    public static void show(String id) {
        Post post = Post.findById(id);
        String randomID = Codec.UUID();
        render(post, randomID);
    }
    
    public static void postComment(
        String postId, 
        @Required(message="Author is required") String author, 
        @Required(message="A message is required") String content, 
        @Required(message="Please type the code") String code, 
        String randomID) 
    {
        Post post = Post.findById(postId);
        if(!Play.id.equals("test")) {
            validation.equals(code, Cache.get(randomID)).message("Invalid code. Please type it again");
        }
        if(Validation.hasErrors()) {
            render("Application/show.html", post, randomID);
        }
        post.addComment(author, content);
        flash.success("Thanks for posting %s", author);
        Cache.delete(randomID);
        show(postId);
    }
    
    public static void captcha(String id) {
        Images.Captcha captcha = Images.captcha();
        String code = captcha.getText("#E4EAFD");
        Cache.set(id, code, "30mn");
        renderBinary(captcha);
    }
    
    public static void listTagged(String tag) {
        List<Post> posts = Post.findTaggedWith(tag);
        render(tag, posts);
    }
 
}
