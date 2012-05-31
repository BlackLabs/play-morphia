import models.UniqueChecker;
import models.edit.CustomKeyChild;
import models.edit.DefaultKeyChild;
import models.edit.Parent;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import play.test.UnitTest;

import java.util.HashMap;

/**
 * The model editing test on references.
 *
 */
public class UniqueCheckTest extends UnitTest
{
    /**
     * Clear the models.
     */
    @Before
    public void setup() {
        UniqueChecker.deleteAll();
    }

    /**
     * Test single relation with custom key.
     */
    @Test
    public void testUniqueChecker() {
        UniqueChecker u = new UniqueChecker("name", "foo", "bar");
        boolean b = u.validateAndSave();
        assertTrue(b);

        u = new UniqueChecker("bar", "name", "foo");
        b = u.validateAndSave();
        assertTrue(b);

        u = new UniqueChecker("name", "foo1", "bar1");
        b = u.validateAndSave();
        assertTrue(b);

        u = new UniqueChecker("name", "foo", "bar");
        b = u.validateAndSave();
        assertFalse(b);
    }

}
