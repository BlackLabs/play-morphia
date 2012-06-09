import java.util.List;

import models.Account;

import org.junit.Before;
import org.junit.Test;

import play.test.UnitTest;


public class QueryTest extends UnitTest {
    
    @Before
    public void setup() {
        Account.deleteAll();
    }
    
    @Test
    public void testFind() {
        Account a1 = new Account("a", "a@a.com", "AU", "IT", 50, 80).save();
        Account a2 = new Account("b", "b@b.com", "AU", "IT", 46, 77).save();
        
        List<Account> l0 = Account.find("region department", "AU", "IT").filter("age >", 30).order("age").asList();
        assertEquals(l0.size(), 2);
        a1 = l0.get(0);
        a2 = l0.get(1);
        assertTrue(a1.age <= a2.age);
        
        l0 = Account.find("byRegionAnddepartment", "AU", "IT").filter("age >", 30).order("-age").asList();
        a1 = l0.get(0);
        a2 = l0.get(1);
        assertTrue(a1.age >= a2.age);

        l0 = Account.find("Region,department", "AU", "IT").filter("age >", 30).order("-age").asList();
        a1 = l0.get(0);
        a2 = l0.get(1);
        assertTrue(a1.age >= a2.age);
    }
    
    @Test
    public void testFindNull() {
        Account a1 = new Account("a", "a@a.com", "AU", "IT", 50, 80).save();
        Account a2 = new Account("b", "b@b.com", "AU", null, 46, 77).save();
        
        List<Account> l0 = Account.q().findBy("department", null).asList();
        assertTrue(l0.size() == 1);
        
        assertEquals(l0.get(0).login, "b");
    }
}
