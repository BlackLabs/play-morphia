import java.io.File;
import java.util.List;

import models.User;

import org.junit.Before;
import org.junit.Test;

import play.modules.morphia.Blob;
import play.modules.morphia.MorphiaPlugin;
import play.test.UnitTest;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFSDBFile;


public class Bug44 extends UnitTest {
    
    private Blob newBlob(String path) {
        return new Blob(new File(path), "image/png");
    }

    @Before
    public void setup() {
        User.deleteAll();
    }
    
    @Test
    public void test() {
        User user = new User();
        user.name = "alex";
        user.photo = newBlob("test/googlelogo.png");
        user.save();

        user.photo = newBlob("test/user.png");
        user.save();
        
        String name = user.getBlobFileName("photo");
        DBObject queryObj = new BasicDBObject("name", name);
        List<GridFSDBFile> list = MorphiaPlugin.gridFs().find(queryObj);
        
        assertEquals(1,list.size());
    }
}
