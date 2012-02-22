import java.lang.String;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import play.test.UnitTest;

import models.*;


public class ExistsQueryTest extends UnitTest {
    
    protected ExistsQueryTestModel model;
    
    @Before
    public void setup() {
        ExistsQueryTestModel.deleteAll();
        model = new ExistsQueryTestModel();
        String[] keys = {"a", "b", "c"};
        for (String s: keys) {
            model.addStuff(s);
        }
        model.save();
    }
    
    @Test
    public void testExistsQuery() {
        ExistsQueryTestModel m = ExistsQueryTestModel.q().filter("bags.z exists", true).get();
        assertNull(m);
        
        m = ExistsQueryTestModel.q().filter("bags.a exists", true).get();
        assertNotNull(m);
    }
    
}