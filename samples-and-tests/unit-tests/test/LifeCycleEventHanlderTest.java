import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import models.Account;
import models.User;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import play.modules.morphia.Model;
import play.modules.morphia.MorphiaEvent.MorphiaEventHandlerAdaptor;
import play.modules.morphia.Watch;
import play.test.UnitTest;


public class LifeCycleEventHanlderTest extends UnitTest {
    public static class GlobalLifecycleEventListener extends MorphiaEventHandlerAdaptor {
        public static GlobalLifecycleEventListener instance;
        public ConcurrentMap<Class<? extends Model>, Integer> updateCounter = new ConcurrentHashMap<Class<? extends Model>, Integer>();
        
        public GlobalLifecycleEventListener() {
            instance = this;
        }
        
        @Override
        public void updated(Model context) {
            Class<? extends Model> c = context.getClass();
            Integer I = updateCounter.get(c);
            if (null == I) {
                updateCounter.put(c, 1);
            } else {
                updateCounter.put(c, ++I);
            }
        }
        
        public void assertTrue(Class<? extends Model> model, int count) {
            Integer I = updateCounter.get(model);
            if (null == I) Assert.assertTrue(0 == count);
            Assert.assertTrue (I == count);
        }
        
        public void resetCounter() {
            updateCounter.clear();
        }
        
    }
    
    @Watch({User.class, Account.class})
    public static class AccountUserLifecycleEventListener extends MorphiaEventHandlerAdaptor {
        public static AccountUserLifecycleEventListener instance;
        public ConcurrentMap<Class<? extends Model>, Integer> updateCounter = new ConcurrentHashMap<Class<? extends Model>, Integer>();
        
        public AccountUserLifecycleEventListener() {
            instance = this;
        }
        
        @Override
        public void updated(Model context) {
            if (context instanceof User || context instanceof Account) {
                Class<? extends Model> c = context.getClass();
                Integer I = updateCounter.get(c);
                if (null == I) {
                    updateCounter.put(c, 1);
                } else {
                    updateCounter.put(c, ++I);
                }
            } else {
                throw new UnsupportedOperationException();
            }
        }
        
        public void assertTrue(Class<? extends Model> model, int count) {
            Integer I = updateCounter.get(model);
            if (null == I) Assert.assertTrue(0 == count);
            Assert.assertTrue(I == count);
        }

        public void resetCounter() {
            updateCounter.clear();
        }
        
    }
    
    protected Account acc;
    protected User usr;
    
    @Before
    public void setup() {
        //MorphiaPlugin.clearAllModelEventHandler();
        //MorphiaPlugin.clearGlobalEventHandler();
        
        Account.deleteAll();
        User.deleteAll();
        
        acc = new Account("green", "green@pixolut.com", "AU", "IT").save();
        usr = new User("green", "testing").save();
        
        GlobalLifecycleEventListener.instance.resetCounter();
        AccountUserLifecycleEventListener.instance.resetCounter();
    }
    
    @Test
    public void testGlobalEventHandler() {
        GlobalLifecycleEventListener l = GlobalLifecycleEventListener.instance;
        
        acc.department = "MGMT";
        acc.save();
        
        usr.tag = "full";
        usr.save();
        
        l.assertTrue(User.class, 1);
        l.assertTrue(Account.class, 1);
    }
    
    @Test
    public void testModelEventHandler() {
        AccountUserLifecycleEventListener l = AccountUserLifecycleEventListener.instance;
        
        acc.department = "MGMT";
        acc.save();
        
        usr.tag = "full";
        usr.save();
        
        l.assertTrue(User.class, 1);
        l.assertTrue(Account.class, 1);
    }
}
