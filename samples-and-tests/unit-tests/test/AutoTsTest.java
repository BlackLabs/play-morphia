import models.AutoTs;

import org.junit.Before;
import org.junit.Test;

import play.Logger;
import play.test.UnitTest;

public class AutoTsTest extends UnitTest {
    
    @Before
    public void setup() {
        AutoTs.deleteAll();
    }
    
    @Test
    public void testAutoTimestamp() throws Exception {
        AutoTs model = new AutoTs();
        model.content = "hello";
        assertSame(model._getCreated(), 0L);
        assertSame(model._getModified(), 0L);
        long ts = System.currentTimeMillis();
        Thread.sleep(1);
        model.save();
        Logger.info("created: %1$s", model._getCreated()); 
        assertTrue(model._getCreated() >= ts);
        assertTrue(model._getModified() >= ts);
        Thread.sleep(1);
        ts = System.currentTimeMillis();
        model.content = "world";
        model.save();
        assertTrue(model._getCreated() < ts);
        assertTrue(model._getModified() >= ts);
    }

}
