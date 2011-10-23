import java.util.List;
import java.util.Set;

import models.Account;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import play.Logger;
import play.modules.morphia.AggregationResult;
import play.test.UnitTest;

public class AccountTest extends UnitTest {

    @Before
    public void setup() {
        Account.deleteAll();
    }

    protected void setUpAggregation() {
        assertTrue(Account.count() == 0);
        
        Account a1 = new Account("loginxyz", "a@a.a", "AU", "IT");
        a1.score = 10;
        a1.save();
        
        a1 = new Account("loginabc", "a@a.x", "AU", "SA");
        a1.score = 20;
        a1.save();
        
        a1 = new Account("login123", "a@a.x", "CN", "IT");
        a1.score = 12;
        a1.save();

        a1 = new Account("login456", "a@a.x", "CN", "SA");
        a1.score = 18;
        a1.save();
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
    
    @Test
    public void testMax() {
        setUpAggregation();
        
        assertSame(4L, Account.count());
        long maxScore = Account._max("score");
        assertSame(20L, maxScore);
        
        maxScore = Account.q("region", "AU").max("score");
        assertSame(20L, maxScore);
        
        AggregationResult r = Account.groupMax("score", "region", "department");
        assertSame(20L, r.getResult("region,department", "AU", "SA"));
        assertSame(12L, r.getResult("region,department", "CN", "IT"));
    }
    
    @Test
    public void testMin() {
        setUpAggregation();
        
        assertSame(10L, Account._min("score"));
        assertSame(12L, Account.q("region", "CN").min("score"));
        
        AggregationResult r = Account.groupMin("score", "department");
        assertSame(18L, r.getResult("department", "SA"));
        
    }
    
    @Test
    public void testSum() {
        setUpAggregation();

        assertSame(60L, Account._sum("score"));
        assertSame(30L, Account.q("region", "AU").sum("score"));
    }
    
    @Test
    public void testAverage() {
        setUpAggregation();

        assertSame(15L, Account._average("score"));
        
        AggregationResult r = Account.groupAverage("score", "department");
        assertSame(19L, r.getResult("department", "SA"));
    }
    
    @Test
    public void testCount() {
        setUpAggregation();
        AggregationResult r = Account.groupCount("score", "region");
        assertSame(2L, r.getResult("region", "CN"));
    }
    
    @Test
    public void testInClause() {
        setUpAggregation();
        String[] regions = {"AU", "CN"};
        List<Account> l = Account.q("region in ", java.util.Arrays.asList(regions)).asList();
        assertSame(4, l.size());
    }

}
