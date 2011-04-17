import java.util.List;

import models.Extend;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import play.Logger;
import play.modules.morphia.MorphiaPlugin;
import play.test.UnitTest;

import org.bson.types.ObjectId;

public class ExtendTest extends UnitTest {
    
    @Before
    public void setup() {
        Extend.deleteAll();
    }
    
    @Test
    public void testFind() {
        Extend extend = new Extend();
        extend.save();
        ObjectId id = (ObjectId)extend.getId();
        Extend e2 = Extend.findById(id);
        assertNotNull(e2);
    }
}