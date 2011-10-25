import models.Event;
import models.LifeCycle;
import models.LifeCycle.Child;

import org.junit.Before;
import org.junit.Test;

import play.modules.morphia.Model.Added;
import play.modules.morphia.Model.BatchDeleted;
import play.modules.morphia.Model.Deleted;
import play.modules.morphia.Model.Loaded;
import play.modules.morphia.Model.MorphiaQuery;
import play.modules.morphia.Model.OnAdd;
import play.modules.morphia.Model.OnBatchDelete;
import play.modules.morphia.Model.OnDelete;
import play.modules.morphia.Model.OnLoad;
import play.modules.morphia.Model.OnUpdate;
import play.modules.morphia.Model.Updated;
import play.test.UnitTest;


public class LifeCycleTest extends UnitTest {
    
    @Before
    public void setup() {
        LifeCycle.deleteAll();
        Event.reset();
        LifeCycle.reset();
    }
    
    @Test
    public void testLifeCycle() {
        LifeCycle lc = new LifeCycle();
        lc.foo = "bar";
        lc.bar = "xx";
        
        lc.save();
        
        t("bar", OnAdd.class, 1);
        t("bar", Added.class, 1);
        t("bar2", OnAdd.class, 1);
        t("bar", Object.class, 2);
        
        lc.bar = "yy";
        lc.save();
        
        t("bar", OnAdd.class, 1);
        t("bar", OnUpdate.class, 1);
        t("bar", Updated.class, 1);
        t("bar2", OnUpdate.class, 1);
        t("bar", Object.class, 4);
        
        lc.bar = "zz";
        lc.save();
        
        t("bar", OnAdd.class, 1);
        t("bar", OnUpdate.class, 2);
        t("bar", Updated.class, 2);
        t("bar2", OnUpdate.class, 2);
        t("bar", Object.class, 6);
        
        lc = LifeCycle.q("foo", "bar").get();
        t("bar", OnAdd.class, 1);
        t("bar", OnUpdate.class, 2);
        t("bar", Updated.class, 2);
        t("bar2", OnUpdate.class, 2);
        t("foo", OnLoad.class, 1);
        t("bar", Loaded.class, 1);
        t("bar", Object.class, 7);
        t("foo", Object.class, 1);
        
        lc.delete();
        t("bar", OnAdd.class, 1);
        t("bar", OnUpdate.class, 2);
        t("bar", Updated.class, 2);
        t("bar2", OnUpdate.class, 2);
        t("bar", OnDelete.class, 1);
        t("bar", Deleted.class, 1);
        t("bar", Object.class, 9);
    }
    
    @Test
    public void testBatchDelete() {
        LifeCycle lc0 = new LifeCycle();
        lc0.foo = "bar";
        lc0.bar = "order";
        lc0.save();
        
        LifeCycle lc1 = new LifeCycle();
        lc1.foo = "foo";
        lc1.bar = "order";
        lc1.save();
        
        LifeCycle.q("bar", "order").delete();
        
        t("foobar", OnBatchDelete.class, 2);
        t("foobar", BatchDeleted.class, 2);
    }
    
    @Test
    public void testExceptionsinLifeCycleMethods() {
        LifeCycle lc = new LifeCycle();
        lc.foo = "bar";
        lc.bar = "xx";
        
        lc.addFail = true;
        try {
            lc.save();
        } catch (Exception e) {
            // ignore
        }
        assertTrue(lc.isNew());
        lc.addFail = false;
        
        lc.addedFail = true;
        try {
            lc.save();
            assertFalse(true);
        } catch (Exception e) {
            // ignore
        }
        assertFalse(lc.isNew());
        lc.addedFail = false;
        
        lc.bar = "yy";
        lc.updateFail = true;
        try {
            lc.save();
        } catch (Exception e) {
            // ignore
        }
        LifeCycle lc1 = LifeCycle.q("foo", "bar").get();
        assertFalse(lc1.bar.equals(lc.bar));
        lc.updateFail = false;
        
        lc.updatedFail = true;
        try {
            lc.save();
            assertFalse(true);
        } catch (Exception e) {
            // ignore
        }
        lc1 = LifeCycle.q("foo", "bar").get();
        assertEquals(lc1.bar, lc.bar);
        lc.updatedFail = false;
        
        LifeCycle.loadFail = true;
        LifeCycle lc2 = null;
        try {
            lc2 = LifeCycle.q("foo", "bar").get();
        } catch (Exception e) {
            // ignore
        }
        assertNull(lc2);
        LifeCycle.loadFail = false;
        
        LifeCycle.loadedFail = true;
        try {
            lc2 = LifeCycle.q("foo", "bar").get();
            assertFalse(true);
        } catch (Exception e) {
            // ignore
        }
        assertNull(lc2);
        
        lc.deleteFail = true;
        try {
            lc.delete();
        } catch (Exception e) {
            // ignore
        }
        assertTrue(LifeCycle.q("foo", "bar").count() > 0);
        lc.deleteFail = false;
        
        lc.deletedFail = true;
        try {
            lc.delete();
            assertFalse(true);
        } catch (Exception e) {
            // ignore
        }
        assertTrue(LifeCycle.q("foo", "bar").count() == 0);
    }
    
    @Test
    public void testExceptionsinBatchLifeCycleMethods() {
        LifeCycle lc0 = new LifeCycle();
        lc0.foo = "bar";
        lc0.bar = "order";
        lc0.save();
        
        LifeCycle lc1 = new LifeCycle();
        lc1.foo = "foo";
        lc1.bar = "order";
        lc1.save();
        
        MorphiaQuery q = LifeCycle.q("bar", "order");
        LifeCycle.batchDeleteFail = true;
        try {
            q.delete();
        } catch (Exception e) {
            // ignore
        }
        assertEquals(q.count(), 2);
        LifeCycle.batchDeleteFail = false;
        
        LifeCycle.batchDeletedFail = true;
        try {
            q.delete();
            assertFalse(true);
        } catch (Exception e) {
            //ignore
        }
        assertEquals(q.count(), 0);
        LifeCycle.batchDeletedFail = false;
    }
    
    @Test
    public void testInheritedLifeCycle() {
        LifeCycle.Child lc = new LifeCycle.Child();
        lc.foo = "bar";
        lc.bar = "xx";
        lc.fee = "123";
        
        lc.save();
        
        t("bar", OnAdd.class, 1);
        t("bar", Added.class, 1);
        t("bar2", OnAdd.class, 1);
        t("bar", Object.class, 2);
        t("123", Added.class, 1);
        t("123", OnAdd.class, 1);

        lc.bar = "yy";
        lc.save();
        
        t("bar", OnAdd.class, 1);
        t("bar", OnUpdate.class, 1);
        t("bar", Updated.class, 1);
        t("bar2", OnUpdate.class, 1);
        t("bar", Object.class, 4);
        t("123", OnUpdate.class, 1);
        t("123", Updated.class, 1);
        
        lc.bar = "zz";
        lc.save();
        
        t("bar", OnAdd.class, 1);
        t("bar", OnUpdate.class, 2);
        t("bar", Updated.class, 2);
        t("bar2", OnUpdate.class, 2);
        t("bar", Object.class, 6);
        t("123", OnUpdate.class, 2);
        t("123", Updated.class, 2);
        
        lc.delete();
        t("bar", OnAdd.class, 1);
        t("bar", OnUpdate.class, 2);
        t("bar", Updated.class, 2);
        t("bar2", OnUpdate.class, 2);
        t("bar", OnDelete.class, 1);
        t("bar", Deleted.class, 1);
        t("bar", Object.class, 8);
        t("123", OnUpdate.class, 2);
        t("123", Updated.class, 2);
        t("123", OnDelete.class, 1);
        t("123", Deleted.class, 1);
    }
    
    @Test
    public void testInheritedBatchDelete() {
        Child lc0 = new Child();
        lc0.foo = "bar";
        lc0.bar = "order";
        lc0.fee = "123";
        lc0.save();
        
        Child lc1 = new Child();
        lc1.foo = "foo";
        lc1.bar = "order";
        lc1.fee = "123";
        lc1.save();
        
        Child.q("bar", "order").delete();
        
        // onBatchDelete is static method so
        // parent hook will not get called
        t("foobar", OnBatchDelete.class, 0); 
        t("foobar", BatchDeleted.class, 0);
        t("456", OnBatchDelete.class, 2);
        t("456", BatchDeleted.class, 2);
    }
    
    private void t(Object id, Class<?> type, int count) {
        assertTrue(Event.count(id, type) == count);
    }

}
