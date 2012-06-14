import java.util.List;

import org.junit.Before;
import org.junit.Test;

import models.Account;
import play.modules.morphia.AggregationResult;
import play.test.UnitTest;


public class AggregationTest extends UnitTest {

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
    public void testMax() {
        setUpAggregation();

        assertSame(4L, Account.count());
        long maxScore = Account._max("sc");
        assertSame(20L, maxScore);

        maxScore = Account.q("region", "AU").max("score");
        assertSame(20L, maxScore);

        AggregationResult r = Account.groupMax("sc", "r", "dep");
        assertSame(20L, r.getResult("r,dep", "AU", "SA"));
        assertSame(12L, r.getResult("region,dep", "CN", "IT"));
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
