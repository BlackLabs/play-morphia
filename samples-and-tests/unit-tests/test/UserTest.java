import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import models.Account;
import models.User;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

import play.modules.morphia.Blob;
import play.modules.morphia.MorphiaPlugin;
import play.test.UnitTest;


public class UserTest extends UnitTest {

    @Before
    public void setup() {
        User.deleteAll();
        MorphiaPlugin.ds().getDB().getCollection(MorphiaPlugin.gridFs().getBucketName() + ".files").drop();
        MorphiaPlugin.ds().getDB().getCollection(MorphiaPlugin.gridFs().getBucketName() + ".chunks").drop();
    }

    @Test
    public void testStoreUser() {
        User u = new User();
        u.name = "alex";
        u = u.save();

        assertFalse(u.isNew());
        assertEquals("alex", u.name);
        assertNull(u.photo);
    }

    @Test
    public void testStoreUserWithAttachment() throws IOException {
        User u = new User();
        u.name = "alex";
        Blob blob = new Blob();
        blob.set(new FileInputStream("test/googlelogo.png"), "image/png");
        u.photo = blob;
        u = u.save();

        assertFalse(u.isNew());
        assertEquals("alex", u.name);

        assertNotNull(u.photo);
        Blob b = u.photo;
        assertThatPhotoBlobIsValid(b);

        // Now load the user from zero
        u = User.find("byName", "alex").first();
        assertThatPhotoBlobIsValid(u.photo);
    }

    private void assertThatPhotoBlobIsValid(Blob blob) throws IOException {
        assertNotNull(blob);

        long originalSize = FileUtils.sizeOf(new File("test/googlelogo.png"));
        assertEquals(originalSize, blob.length());
        assertEquals("image/png", blob.type());
        InputStream is = blob.get();
        assertNotNull(is);

        String actualMd5 = DigestUtils.md5Hex(is);
        String expectedMd5 = DigestUtils.md5Hex(new FileInputStream("test/googlelogo.png"));
        assertEquals(expectedMd5, actualMd5);

    }
}
