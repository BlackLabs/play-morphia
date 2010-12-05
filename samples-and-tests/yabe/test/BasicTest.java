import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import models.Comment;
import models.Post;
import models.Tag;
import models.User;

import org.junit.Before;
import org.junit.Test;

import play.modules.morphia.MorphiaPlugin;
import play.modules.morphia.utils.MorphiaFixtures;
import play.test.Fixtures;
import play.test.UnitTest;

import com.google.code.morphia.Datastore;
 
public class BasicTest extends UnitTest {
    protected Datastore ds = MorphiaPlugin.ds();
    
    @Before
    public void setup() {
        MorphiaFixtures.deleteAll();
    }
 
    @Test
    public void createAndRetrieveUser() {
        // Create a new user and save it
        new User("bob@gmail.com", "secret", "Bob").save();

        // Retrieve the user with bob username
        User bob = User.<User>find("byEmail", "bob@gmail.com").first();

        // Test 
        assertNotNull(bob);
        assertEquals("Bob", bob.fullname);
    }
    
    @Test
    public void tryConnectAsUser() {
        // Create a new user and save it
        new User("bob@gmail.com", "secret", "Bob").save();

        // Test 
        assertNotNull(User.connect("bob@gmail.com", "secret"));
        assertNull(User.connect("bob@gmail.com", "badpassword"));
        assertNull(User.connect("tom@gmail.com", "secret"));
    }
    
    @Test
    public void createPost() {
        // Create a new user and save it
        User bob = new User("bob@gmail.com", "secret", "Bob");
        bob.save();

        // Create a new post
        new Post(bob, "My first post", "Hello world").save();

        // Test that the post has been created
        assertEquals(1, Post.count());

        // Retrieve all post created by bob
        List<Post> bobPosts = Post.<Post>find("byAuthor", bob).asList();

        // Tests
        assertEquals(1, bobPosts.size());
        Post firstPost = bobPosts.get(0);
        assertNotNull(firstPost);
        assertEquals(bob, firstPost.author);
        assertEquals("My first post", firstPost.title);
        assertEquals("Hello world", firstPost.content);
        assertNotNull(firstPost.postedAt);
    }
    
    @Test
    public void postComments() {
        // Create a new user and save it
        User bob = new User("bob@gmail.com", "secret", "Bob");
        bob.save();

        // Create a new post
        Post bobPost = new Post(bob, "My first post", "Hello world");
        bobPost.save();

        // Post a first comment
        new Comment(bobPost, "Jeff", "Nice post").save();
        new Comment(bobPost, "Tom", "I knew that !").save();

        // Retrieve all comments
        List<Comment> bobPostComments = Comment.<Comment>find("byPost", bobPost).asList();

        // Tests
        assertEquals(2, bobPostComments.size());

        Comment firstComment = bobPostComments.get(0);
        assertNotNull(firstComment);
        assertEquals("Jeff", firstComment.author);
        assertEquals("Nice post", firstComment.content);
        assertNotNull(firstComment.postedAt);

        Comment secondComment = bobPostComments.get(1);
        assertNotNull(secondComment);
        assertEquals("Tom", secondComment.author);
        assertEquals("I knew that !", secondComment.content);
        assertNotNull(secondComment.postedAt);
    }
    
    @Test
    public void useTheCommentsRelation() {
        // Create a new user and save it
        User bob = new User("bob@gmail.com", "secret", "Bob");
        bob.save();

        // Create a new post
        Post bobPost = new Post(bob, "My first post", "Hello world").save();

        // Post a first comment
        bobPost.addComment("Jeff", "Nice post");
        bobPost.addComment("Tom", "I knew that !");

        // Count things
        assertEquals(1, User.count());
        assertEquals(1, Post.count());
        assertEquals(2, bobPost.comments.size());

        // Retrieve the bob post
        bobPost = Post.<Post>find("byAuthor", bob).first();
        assertNotNull(bobPost);

        // Navigate to comments
        assertEquals(2, bobPost.comments.size());
        assertEquals("Jeff", bobPost.comments.get(0).author);

        // Delete the post
        bobPost.delete();

        // Chech the all comments have been deleted
        assertEquals(1, User.count());
        assertEquals(0, Post.count());
        assertEquals(0, Comment.count());
    }
    
    @Test
    public void fullTest() {
        Fixtures.load("initial-data.yml");

        // Count things
        assertEquals(3, User.count());
        assertEquals(3, Post.count());
        // yml load does not support embedded items assertEquals(3, Comment.count());

        // Try to connect as users
        assertNotNull(User.connect("bob@gmail.com", "secret"));
        assertNotNull(User.connect("jeff@gmail.com", "secret"));
        assertNull(User.connect("jeff@gmail.com", "badpassword"));
        assertNull(User.connect("tom@gmail.com", "secret"));

        // Find all bob posts
        User u = User.<User>filter("email", "bob@gmail.com").first();
        List<Post> bobPosts = Post.<Post>filter("author", u).asList();
        assertEquals(2, bobPosts.size());

        // Find all comments related to bob posts
        /* yml load does not support embedded items
        List<Comment> bobComments = Comment.filter("post.author.email", "bob@gmail.com").asList();
        assertEquals(3, bobComments.size());
        */

        // Find the most recent post
        Post frontPost = (Post) Post.find().order("-postedAt").get();
        assertNotNull(frontPost);
        assertEquals("About the model layer", frontPost.title);

        // Check that this post has two comments
        // yml load does not support embedded items: assertEquals(2, frontPost.comments.size());

        // Post a new comment
        frontPost.addComment("Jim", "Hello guys");
        assertEquals(1, frontPost.comments.size());
        // yml load does not support embedded items: assertEquals(3, frontPost.comments.size());
        // yml load does not support embedded items: assertEquals(4, Comment.count());
    }
    
    @Test
    public void testTags() {
        // Create a new user and save it
        User bob = new User("bob@gmail.com", "secret", "Bob").save();

        // Create a new post
        Post bobPost = new Post(bob, "My first post", "Hello world").save();
        Post anotherBobPost = new Post(bob, "My second post post", "Hello world").save();
        
        // Well
        assertEquals(0, Post.findTaggedWith("Red").size());
        
        // Tag it now
        bobPost.tagItWith("Red").tagItWith("Blue").save();
        anotherBobPost.tagItWith("Red").tagItWith("Green").save();
        
        // Check
        assertEquals(2, Post.findTaggedWith("Red").size());        
        assertEquals(1, Post.findTaggedWith("Blue").size());
        assertEquals(1, Post.findTaggedWith("Green").size());
        
        assertEquals(1, Post.findTaggedWith("Red", "Blue").size());   
        assertEquals(1, Post.findTaggedWith("Red", "Green").size());   
        assertEquals(0, Post.findTaggedWith("Red", "Green", "Blue").size());  
        assertEquals(0, Post.findTaggedWith("Green", "Blue").size());    
        
        List<Map<String, Integer>> cloud = Tag.getCloud();
        Collections.sort(cloud, new Comparator<Map<String, Integer>>() {
            public int compare(Map<String, Integer> m1, Map<String, Integer> m2) {
                return m1.keySet().iterator().next().compareTo(m2.keySet().iterator().next());
            }
        });
        assertEquals("[{Blue=1}, {Green=1}, {Red=2}]", cloud.toString());
        
    }
    
    @Test
    public void testIsNew() {
        User bob = new User("bob@gmail.com", "secret", "Bob");
        assertTrue(bob.isNew());
        bob.save();
        assertFalse(bob.isNew());
        
        //User b2 = User.<User>filter("email", "bob@gmail.com").get();
        assertFalse(bob.isNew());
    }
 
}