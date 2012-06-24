import java.util.Set;

import models.Account;

import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import play.Logger;
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
        String id = acc.getId();
        assertNotNull(id);
    }

    @Test
    public void testTransientField() {
        Account acc = new Account("xxx", "a@a.com");
        acc.foo = "bar";
        acc.save();
        acc = Account.find("login", "xxx").get();
        assertNotNull(acc);
        assertNull(acc.foo);
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

    @Test
    public void testDistinct() {
        Account a1 = new Account("loginxyz", "a@a.a");
        a1.save();
        Account a2 = new Account("loginabc", "a@a.x");
        a2.save();
        Set<?> set = Account._distinct("email");
        assertSame(2, set.size());
        Account a3 = new Account("login123", "a@a.b", "SG");
        a3.save();
        Account a4 = new Account("login456", "a@a.c", "AU");
        a4.save();
        set = Account.q("region", "AU").distinct("email");
        assertSame(3, set.size());
        assertTrue(set.contains("a@a.a"));
        assertTrue(set.contains("a@a.x"));
        assertTrue(set.contains("a@a.c"));
        assertFalse(set.contains("a@a.b"));
    }

}
