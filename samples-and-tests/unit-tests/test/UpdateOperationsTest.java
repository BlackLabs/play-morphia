import java.util.Set;

import models.Account;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import play.Logger;
import play.test.UnitTest;

import javax.security.auth.login.AccountException;


public class UpdateOperationsTest extends UnitTest {

    @Before
    public void setup() {
        Account.deleteAll();
        new Account("abc", "abc@1.com", "au", "it", 29, 77).save();
        new Account("xyz", "xyz@1.com", "au", "it", 32, 89).save();
    }

    @Test
    public void testUpdateFirst() {
        Account acc0 = Account.o().inc("age").updateFirst(Account.q().filter("region", "au"));
        Account acc = Account.q().filter("login", "abc").get();
        assertEquals(acc0, acc);
        assertEquals(30, acc.age);
        acc = Account.q().filter("login", "xyz").get();
        assertEquals(32, acc.age);
    }

    @Test
    public void testUpdateAll() {
        Account.o().inc("age").update(Account.q().filter("region", "au"));
        Account acc = Account.q().filter("login", "abc").get();
        assertEquals(30, acc.age);
        acc = Account.q().filter("login", "xyz").get();
        assertEquals(33, acc.age);
    }

    @Test
    public void testUpdateNull() {
        Account acc = Account.o().inc("age").updateFirst(Account.q().filter("region", "cn"));
        assertNull(acc);
    }

    public void testUpdateSet() {
        Account.o().set("byAgeAndRegion", 100, "cn").updateAll();
        Account acc = Account.q().filter("login", "abc").get();
        assertEquals(100, acc.age);
        assertEquals("cn", acc.region);
        acc = Account.q().filter("login", "xyz").get();
        assertEquals(100, acc.age);
        assertEquals("cn", acc.region);
    }

}