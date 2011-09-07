import models.Account;

import org.junit.Before;
import org.junit.Test;

import play.test.UnitTest;
import plugins.EventTestPlugin;


public class PluginEventTest extends UnitTest {

    @Before
    public void setup() {
        Account.deleteAll();
        EventTestPlugin.events.clear();
    }

    @Test
    public void testCreationEventIsEmitted() throws Exception {
        Account account = new Account("foo", "bar");
        account = account.save();

        assertEquals(EventTestPlugin.events.size(), 1);
        assertTrue(EventTestPlugin.events.containsKey("MorphiaSupport.objectPersisted"));
        assertTrue(EventTestPlugin.events.containsValue(account));
    }

    @Test
    public void testUpdateEventIsEmitted() throws Exception {
        Account account = new Account("foo", "bar");
        account = account.save();
        EventTestPlugin.events.clear();

        account.login = "upadte";
        account = account.save();

        assertEquals(EventTestPlugin.events.size(), 1);
        assertTrue(EventTestPlugin.events.containsKey("MorphiaSupport.objectUpdated"));
        assertTrue(EventTestPlugin.events.containsValue(account));
    }

    @Test
    public void testDeleteEventIsEmitted() throws Exception {
        Account account = new Account("foo", "bar");
        account = account.save();
        EventTestPlugin.events.clear();

        account = account.delete();

        assertEquals(EventTestPlugin.events.size(), 1);
        assertTrue(EventTestPlugin.events.containsKey("MorphiaSupport.objectDeleted"));
        assertTrue(EventTestPlugin.events.containsValue(account));
    }

}
