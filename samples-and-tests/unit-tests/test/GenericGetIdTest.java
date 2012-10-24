import models.Account;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import play.Logger;
import play.test.UnitTest;

import java.util.Set;

public class GenericGetIdTest extends UnitTest {

    @Before
    public void setup() {
        Account.deleteAll();
    }

    @Test
    public void testGenericGetId() {
        Account before = new Account("loginxyz", "a@a.a");
        before.save();
        String id = before.getId();
    }

}
