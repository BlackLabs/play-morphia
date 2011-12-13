import java.util.Set;

import models.Account;

import org.junit.Assert;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import play.Logger;
import play.Play;
import play.modules.morphia.MorphiaPlugin;
import play.modules.morphia.ThreadLocalTenantContext;
import play.test.UnitTest;

public class MultitenantTest extends UnitTest {

    @Before
    public void setup() {
        MorphiaPlugin.setMultitenantMode(true);
        MorphiaPlugin.setTenantContext(new ThreadLocalTenantContext());
        ThreadLocalTenantContext.setTenant("tenant1");
        Account.deleteAll();
        ThreadLocalTenantContext.setTenant("tenant2");
        Account.deleteAll();
    }
    @After
    public void tearDown() {
        MorphiaPlugin.setMultitenantMode(false);
    }

    @Test
    public void testTwoTenants() {
        ThreadLocalTenantContext.setTenant("tenant1");
        Assert.assertEquals(0, Account.count());
        Account acc = new Account("loginxyz", "a@a.a");
        acc.save();
        Assert.assertEquals(1, Account.count());
        
        ThreadLocalTenantContext.setTenant("tenant2");
        Assert.assertEquals(0, Account.count());
        Account acc2 = new Account("loginxyz", "a@a.a");
        acc2.save();
        Assert.assertEquals(1, Account.count());
        Account acc3 = new Account("loginxyz2", "a@a.a2");
        acc3.save();
        Assert.assertEquals(2, Account.count());

        ThreadLocalTenantContext.setTenant("tenant1");
        Assert.assertEquals(1, Account.count());

        ThreadLocalTenantContext.setTenant("tenant2");
        Assert.assertEquals(2, Account.count());
        
        
    }


}
