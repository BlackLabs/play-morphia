import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import models.User;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import play.modules.morphia.Blob;
import play.modules.morphia.MorphiaPlugin;
import play.test.UnitTest;

import com.mongodb.gridfs.GridFSDBFile;


public class UserTest extends UnitTest {

    private Blob blob;
    private long fileLen;

    @Before
    public void setup() throws FileNotFoundException {
        User.deleteAll();
        MorphiaPlugin.ds().getDB().getCollection(MorphiaPlugin.gridFs().getBucketName() + ".files").drop();
        MorphiaPlugin.ds().getDB().getCollection(MorphiaPlugin.gridFs().getBucketName() + ".chunks").drop();

        blob = newBlob();
    }
    
    private Blob newBlob() {
        return newBlob("test/googlelogo.png");
    }
    
    private Blob newBlob(String path) {
        return new Blob(new File(path), "image/png");
    }

     @Test
    public void checkFieldAccessibility() throws Exception {
        User u = new User();
        u.name = "alex";
        u.photo = blob;
        u.save();

        Object name = u.getClass().getField("name").get(u);
        assertNotNull(name);
        assertEquals("alex", name);;

        assertNotNull(u.photo);
        assertThatPhotoBlobIsValid(u.photo.getGridFSFile());
        // TODO: Weird case. In this example it works, whereas in CRUD.attachment() this code always returns null
        // I am unable to find out, what is broken in the CRUD code right now
        // My first shot was about the propertiesEnhancer and its changing of field accessors. But why is this code working then?
        Object att = u.getClass().getField("photo").get(u);
        assertNotNull(att);
        assertThatPhotoBlobIsValid(((Blob) att).getGridFSFile());
    }

    @Test
    public void testStoreUser() {
        User u = new User();
        u.name = "alex";
        assertTrue(u.isNew());
        u.save();

        assertFalse(u.isNew());
        assertEquals("alex", u.name);
        assertNull(u.photo);
    }

    @Test
    public void testStoreUserWithAttachment() throws IOException {
        User u = new User();
        u.name = "alex";
        u.photo = blob;
        assertTrue(u.isNew());
        u = u.save();

        assertFalse(u.isNew());
        assertEquals("alex", u.name);

        assertNotNull(u.photo);
        Blob b = u.photo;
        assertThatPhotoBlobIsValid(b.getGridFSFile());

        // Now load the user from zero
        u = User.find("byName", "alex").first();
        assertFalse(u.isNew());
        assertThatPhotoBlobIsValid(u.photo.getGridFSFile());
        assertThatPhotoBlobIsValid(u.photo.getGridFSFile());
    }
    
    @Test
    public void testDeleteBlob() throws Exception {
        User u = new User();
        u.name = "alex";
        u.photo = blob;
        u.save();

        GridFSDBFile file = Blob.findFile(u.getBlobFileName("photo"));
        
        assertThatPhotoBlobIsValid(file);
        u.delete();

        file = Blob.findFile(u.getBlobFileName("photo"));
        
        assertNull(file);
    }
    
    @Test
    public void testBatchDeleteBlob() throws Exception {
        User a = new User();
        a.name = "alex";
        a.tag = "testing";
        a.photo = newBlob();
        a.save();

        User b = new User();
        b.name = "bob";
        b.tag = "testing";
        b.photo = newBlob();
        b.save();
        
        GridFSDBFile file = Blob.findFile(a.getBlobFileName("photo"));
        assertThatPhotoBlobIsValid(file);

        file = Blob.findFile(b.getBlobFileName("photo"));
        assertThatPhotoBlobIsValid(file);
        
        b.photo = newBlob("test/user.png");
        b.save();
        file = Blob.findFile(b.getBlobFileName("photo"));
        assertThatPhotoBlobIsValid(file, "user.png");

        User.q("tag", "testing").delete();
        
        file = Blob.findFile(a.getBlobFileName("photo"));
        assertNull(file);

        file = Blob.findFile(b.getBlobFileName("photo"));
        assertNull(file);
    }
    
    private void assertThatPhotoBlobIsValid(GridFSDBFile file) throws IOException {
        assertThatPhotoBlobIsValid(file, "googlelogo.png");
    }
    

    private void assertThatPhotoBlobIsValid(GridFSDBFile file, String fileName) throws IOException {
        assertNotNull(file);

        fileLen = FileUtils.sizeOf(new File("test/" + fileName));
        assertEquals(fileLen, file.getLength());
        assertEquals("image/png", file.getContentType());
        InputStream is = file.getInputStream();
        assertNotNull(is);

        String actualMd5 = DigestUtils.md5Hex(is);
        String expectedMd5 = DigestUtils.md5Hex(new FileInputStream("test/" + fileName));
        assertEquals(expectedMd5, actualMd5);

        assertNotNull(file.getFilename());
        assertEquals(file.getFilename(), fileName);
    }
}
