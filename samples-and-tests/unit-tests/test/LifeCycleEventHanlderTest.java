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
import play.modules.morphia.WatchBy;
import play.test.UnitTest;

import com.google.code.morphia.annotations.Entity;


public class LifeCycleEventHanlderTest extends UnitTest {
    @Watch
    public static class GlobalLifecycleEventListener extends MorphiaEventHandlerAdaptor {
        public static ConcurrentMap<Class<? extends Model>, Integer> updateCounter = new ConcurrentHashMap<Class<? extends Model>, Integer>();
        
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
        
        public static void assertTrue(Class<? extends Model> model, int count) {
            Integer I = updateCounter.get(model);
            if (null == I) Assert.assertTrue(0 == count);
            Assert.assertTrue (I == count);
        }
        
        public static void resetCounter() {
            updateCounter.clear();
        }
        
    }
    
    @Watch({User.class, Account.class})
    public static class AccountUserLifecycleEventListener extends MorphiaEventHandlerAdaptor {
        public static ConcurrentMap<Class<? extends Model>, Integer> updateCounter = new ConcurrentHashMap<Class<? extends Model>, Integer>();
        
        @Override
        public void updated(Model context) {
            if (context instanceof User || context instanceof Account || context instanceof ModelA) {
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
        
        public static void assertTrue(Class<? extends Model> model, int count) {
            Integer I = updateCounter.get(model);
            if (null == I) Assert.assertTrue(0 == count);
            Assert.assertTrue(I == count);
        }

        public static void resetCounter() {
            updateCounter.clear();
        }
        
    }
    
    @SuppressWarnings("serial")
    @Entity
    @WatchBy(AccountUserLifecycleEventListener.class)
    public static class ModelA extends Model {
        
        public String foo;
    } 
    
    protected Account acc;
    protected User usr;
    protected ModelA ma;
    
    @Before
    public void setup() {
        //MorphiaPlugin.clearAllModelEventHandler();
        //MorphiaPlugin.clearGlobalEventHandler();
        
        Account.deleteAll();
        User.deleteAll();
        ModelA.deleteAll();
        
        acc = new Account("green", "green@pixolut.com", "AU", "IT").save();
        usr = new User("green", "testing").save();
        ma = new ModelA().save();
        
        GlobalLifecycleEventListener.resetCounter();
        AccountUserLifecycleEventListener.resetCounter();
    }
    
    @Test
    public void testGlobalEventHandler() {
        acc.department = "MGMT";
        acc.save();
        
        usr.tag = "full";
        usr.save();
        
        GlobalLifecycleEventListener.assertTrue(User.class, 1);
        GlobalLifecycleEventListener.assertTrue(Account.class, 1);
    }
    
    @Test
    public void testModelEventHandler() {
        acc.department = "MGMT";
        acc.save();
        
        usr.tag = "full";
        usr.save();
        
        AccountUserLifecycleEventListener.assertTrue(User.class, 1);
        AccountUserLifecycleEventListener.assertTrue(Account.class, 1);
    }
    
    @Test
    public void testWatchBy() {
        ma.foo = "bar";
        ma.save();
        
        AccountUserLifecycleEventListener.assertTrue(ModelA.class, 1);
    }
}
