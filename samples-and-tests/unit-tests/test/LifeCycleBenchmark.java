import models.LegacyLifeCycle;
import models.NewLifeCycle;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import play.Logger;
import play.test.UnitTest;


public class LifeCycleBenchmark extends UnitTest {
    
    @Before
    public void setup() {
        // warm up
        for (int i = 0; i < 5000; ++i) {
            new NewLifeCycle("foo", "bar").save();
        }
        for (int i = 0; i < 5000; ++i) {
            new LegacyLifeCycle("foo", "bar").save();
        }
    }
    
    @After
    public void teardown() {
        NewLifeCycle.deleteAll();
        LegacyLifeCycle.deleteAll();
    }

    @Test
    public void benchmark() {
        long d0 = 0, d1 = 0;
        
        d1 += n(10000);
        d0 += l(10000);

        d1 += n(10000);
        d0 += l(10000);

        d1 += n(10000);
        d0 += l(10000);

        d1 += n(10000);
        d0 += l(10000);

        d1 += n(10000);
        d0 += l(10000);

        Logger.info("legacy: %s", d0);
        Logger.info("new: %s", d1);

        assertTrue(d1 < d0);
    }
    
    private long l(int times) {
        long l1 = System.currentTimeMillis();
        for (int i = 0; i < times; ++i) {
            new LegacyLifeCycle("foo", "bar").save();
        }
        return System.currentTimeMillis() - l1;
    }

    private long n(int times) {
        long l1 = System.currentTimeMillis();
        for (int i = 0; i < times; ++i) {
            new NewLifeCycle("foo", "bar").save();
        }
        return System.currentTimeMillis() - l1;
    }
}
