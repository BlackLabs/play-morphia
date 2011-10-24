import models.User;
import models.User2;

import org.junit.Before;
import org.junit.Test;

import play.test.UnitTest;


public class ColumnAnnotationTest extends UnitTest {

    @Before
    public void setup() {
        User.deleteAll();
    }
    
    @Test
    public void test() {
        User u0 = new User();
        u0.name = "Foo";
        u0.tag = "sdfu893749";
        u0.save();
        
        User2 u1 = User2.filter("nm", "Foo").get();
        assertNotNull(u1);
        assertEquals(u1.tag, u0.tag);
    }
    
}
