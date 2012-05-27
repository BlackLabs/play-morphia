

import models.Parent;
import models.Child;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import play.Logger;
import play.test.UnitTest;
import play.test.MorphiaFixtures;

/**
 * Test the correct loading of references through yml files
 */
public class ReferenceYMLTest extends UnitTest {

    @Before
    public void setup() {
    	Logger.info("Deleting data...");
    	MorphiaFixtures.delete(Child.class, Parent.class);
    	Logger.info("Inserting data...");
    	MorphiaFixtures.loadModels("initial-data.yml");
    }

    @Test
    public void testSimpleReference() {
       Parent parent = Parent.find("byParentName","parent").first();
       Assert.assertNotNull("Parent not found!", parent);
       Assert.assertNotNull("Child is null, reference didn't load correctly",parent.child);
    }
}
