import models.WithCache;
import models.WithOutCache;
import org.junit.Before;
import org.junit.Test;
import play.Logger;
import play.test.UnitTest;

import java.util.Random;

public class CacheEntityTest extends UnitTest {

    private String wcId = null;
    private String ncId = null;
    private WithCache _wc = null;
    private WithOutCache _nc = null;

    @Before
    public void setUp() {
        Random r = new Random();
        WithCache wc = WithCache.q().offset(r.nextInt(500) + 10).get();
        _wc = wc;
        wcId = wc.getId();
        WithOutCache nc = WithOutCache.q().offset(r.nextInt(500) + 10).get();
        _nc = nc;
        ncId = nc.getId();
    }

    @Test
    public void testCacheEntity() throws Exception {
        WithCache wc = WithCache.findById(wcId);
        assertNotNull(wc);
        assertEquals(wc, _wc);
        _wc.f1 = "xx";
        _wc.save();
        wc = WithCache.findById(wcId);
        assertEquals(wc.f1, "xx");
        _wc._update("f1", "yy");
        wc = WithCache.findById(wcId);
        assertEquals(wc.f1, "yy");
    }

    @Test
    public void benchmark() throws Exception {
        long ts = System.currentTimeMillis();
        for (int i = 0; i < 10000; ++i) {
            WithOutCache.findById(ncId);
        }
        long  nc = System.currentTimeMillis() - ts;
        ts = System.currentTimeMillis();
        for (int i = 0; i < 10000; ++i) {
            WithCache.findById(wcId);
        }
        long wc = System.currentTimeMillis() - ts;
        System.out.println("cache: " + wc + ", no cache: " + nc);
        assertTrue(wc < nc);
    }

}
