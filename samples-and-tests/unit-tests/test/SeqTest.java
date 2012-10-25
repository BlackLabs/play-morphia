
import com.google.code.morphia.Datastore;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFSDBFile;
import models.User;
import org.junit.Before;
import org.junit.Test;
import play.modules.morphia.Blob;
import play.modules.morphia.MorphiaPlugin;
import play.modules.morphia.Seq;
import play.test.UnitTest;

import java.io.File;
import java.util.List;


public class SeqTest extends UnitTest {

    @Before
    public void setup() {
        Datastore ds = MorphiaPlugin.ds();
        ds.delete(ds.createQuery(Seq.class));
    }

    @Test
    public void test() {
        long l = Seq.nextValue();
        assertEquals(l, 1);
        l = Seq.nextValue();
        assertEquals(l, 2);
        l = Seq.nextValue("foo");
        assertEquals(l, 1);
        String s = Seq.next().getAsString(8);
        assertEquals("00000003", s);
        for (int i = 0; i < 100; ++i) Seq.next("foo");
        s = Seq.next("foo").getAsString("bar", 2);
        assertEquals("bar102", s);
    }
}
