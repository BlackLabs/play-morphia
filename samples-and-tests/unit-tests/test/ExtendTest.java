import models.*;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;

import play.test.UnitTest;

public class ExtendTest extends UnitTest {
    
    @Before
    public void setup() {
        Extend.deleteAll();
        Extend2.deleteAll();
    }
    
    @Test
    public void testFind() {
        Extend extend = new Extend();
        extend.save();
        Object id = extend.getId();
        extend = Extend.findById(id);
        assertNotNull(extend);
        
//        Base base = Base.findById(id);
//        assertNotNull(base);

        long count = -1;

        Extend2 e2 = new Extend2();
        e2.save();
        id = e2.getId();

        e2 = Extend2.findById(id);
        assertNotNull(e2);

        count = Extend2.count();
        assertEquals(1l, count);

        count = Extend.count();
        assertEquals(1l, count);
        
    }

}