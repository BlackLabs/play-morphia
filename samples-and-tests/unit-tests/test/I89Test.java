import models.User;
import org.junit.Before;
import org.junit.Test;
import play.modules.morphia.MorphiaPlugin;
import play.test.UnitTest;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 19/07/12
 * Time: 6:23 AM
 * To change this template use File | Settings | File Templates.
 */
public class I89Test extends UnitTest {
    @Before
    public void setup() throws FileNotFoundException {
        User.deleteAll();
    }

    // batch insert some data and see if the ids are in String type
    @Test
    public void batchInsertTest() {
        User user = new User();
        user.name = "i89";
        List<User> ul = new ArrayList<User>();
        ul.add(user);
        User.insert(ul);
        user = User.get();
        Object id = user.getId();
        assertTrue(id instanceof String);
    }

}
