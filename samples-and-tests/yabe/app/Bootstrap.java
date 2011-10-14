import java.text.DateFormat;
import java.text.ParseException;

import models.Comment;
import models.Post;
import models.User;
import play.jobs.Job;
import play.jobs.OnApplicationStart;
import play.test.Fixtures;
 
@OnApplicationStart
public class Bootstrap extends Job<Object> {
 
    public void doJob() {
        
        // Check if the database is empty
        if(User.count() == 0) {
            DateFormat df = DateFormat.getDateInstance();
            Fixtures.load("initial-data.yml");
            /* Morphia cannot load embedded object automatically so here we go */
            User bob = (User) User.filter("fullname", "Bob").get();
            Post firstBobPost = (Post) Post.filter("author", bob).field("title").contains("model").get();
            Comment c1 = new Comment(firstBobPost, "Guest", "You are right !");
            try {
                c1.postedAt = df.parse("2009-06-14");
            } catch (ParseException e1) {
                e1.printStackTrace();
            }
            
            Comment c2 = new Comment(firstBobPost, "Mike", "I knew that ...");
            try {
                c2.postedAt = df.parse("2009-06-15");
            } catch (ParseException e) {
                e.printStackTrace();
            }
            
            Post secondBobPost = (Post)Post.filter("author", bob).field("title").endsWith("YABE").get();
            Comment c3 = new Comment(secondBobPost, "Tom", "This post is useless ?");
            try {
                c3.postedAt = df.parse("2009-04-05");
            } catch (ParseException e) {
                e.printStackTrace();
            }
            
            firstBobPost.save();
            secondBobPost.save();
        }
    }
 
}