import java.util.List;

import models.AutoTs;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import play.Logger;
import play.modules.morphia.MorphiaPlugin;
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
        model.content = "world";
        model.save();
        ts = System.currentTimeMillis();
        assertTrue(model._getCreated() < ts);
        assertTrue(model._getModified() >= ts);
    }

}
