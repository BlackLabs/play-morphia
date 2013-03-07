import models.Account;
import models.UserDefinedID;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import play.Logger;
import play.test.UnitTest;

import java.util.Set;

public class UserDefinedIDTest extends UnitTest {

    protected String id;

    @Before
    public void setup() {
        UserDefinedID.deleteAll();
        for (int i = 0; i < 1000; ++i) {
            UserDefinedID udd = new UserDefinedID().save();
            if (i == 500) id = udd.name;
        }
    }

    @Test
    public void testFindById() {
        UserDefinedID udd = UserDefinedID.findById(id);
        assertNotNull(udd);
        assertEquals(udd.name, id);
    }


}
