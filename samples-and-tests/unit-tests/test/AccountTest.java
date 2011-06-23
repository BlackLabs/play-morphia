import java.util.List;

import models.Account;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import play.Logger;
import play.modules.morphia.MorphiaPlugin;
import play.test.UnitTest;

public class AccountTest extends UnitTest {

    @Before
    public void setup() {
        Account.deleteAll();
    }

    @Test
    public void testDeleteAll() {
        Account before = new Account("loginxyz", "a@a.a");
        before.save();
        Account.deleteAll();
        Assert.assertEquals(0, Account.count());
    }

    @Test
    public void testDelete() {
        Account before = new Account("loginxyz", "a@a.a");
        before.save();
        Assert.assertEquals(1, Account.count());
        before.delete();
        Assert.assertEquals(0, Account.count());
    }

    @Test
    public void testFindAll() {
        Account.findAll();
    }

    @Test
    public void testIdAfterSaved() {
        Account acc = new Account("loginxyz", "a@a.a");
        acc.save();
        assertNotNull(acc.getId());
    }

    @Test
    public void testFindByNullId() {
        Account acc = new Account("loginxyz", "a@a.a");
        acc.save();
        Object id = acc.getId();
        assertNotNull(id);
        acc = Account.findById(acc.getId());
        assertEquals(acc.login, "loginxyz");
        assertNull(Account.findById(null));
        //assertNotNull(Account.findById(acc.getId()).get());
        //assertNull(Account.findById(null));
    }

    @Test
    public void testUnique() {
        Account before = new Account("loginxyz", "a@a.a");
        before.save();
        before = new Account("loginxyz1", "a@a.a");
        try {
            before.save();
            Logger.info("count: %1$s", Account.count());
        } catch (Exception e) {
        	Logger.info("count1: %1$s", Account.count());
            assertTrue(true);
            return;
        }
        Logger.info("count2: %1$s", Account.count());
    }

}
