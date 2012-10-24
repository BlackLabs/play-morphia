import models.I95Model;
import models.User;
import org.junit.Before;
import org.junit.Test;
import play.test.UnitTest;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 24/10/12
 * Time: 11:13 AM
 * To change this template use File | Settings | File Templates.
 */
public class I95Test extends UnitTest {
    @Before
    public void setup() throws FileNotFoundException {
        I95Model.deleteAll();
    }

    // batch insert some data and see if the ids are in String type
    @Test
    public void test() {
        I95Model m = new I95Model("a", "b", "c");
        m.save();

        m = I95Model.findById(m.getId());
        assertEquals("c", m.e.c);
    }
}
