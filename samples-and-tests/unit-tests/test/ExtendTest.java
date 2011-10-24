import models.Extend;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;

import play.test.UnitTest;

public class ExtendTest extends UnitTest {
    
    @Before
    public void setup() {
        Extend.deleteAll();
    }
    
    @Test
    public void testFind() {
        Extend extend = new Extend();
        extend.save();
        ObjectId id = (ObjectId)extend.getId();
        Extend e2 = Extend.findById(id);
        assertNotNull(e2);
    }
}