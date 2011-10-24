package models;

import java.util.HashMap;
import java.util.Map;

import play.modules.morphia.Model;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Transient;

@SuppressWarnings("serial")
@Entity
public class LifeCycle extends Model {
    
    public static boolean loadFail = false;
    public static boolean loadedFail = false;
    
    @Transient public boolean addFail = false;
    @Transient public boolean updateFail = false;
    @Transient public boolean addedFail = false;
    @Transient public boolean updatedFail = false;
    @Transient public boolean deleteFail = false;
    @Transient public boolean deletedFail = false;
    
    public static boolean batchDeleteFail = false;
    public static boolean batchDeletedFail = false;
    
    public static void reset() {
        loadFail = false;
        loadedFail = false;
        batchDeletedFail = false;
        batchDeleteFail = false;
    }
    
    public String foo;
    
    public String bar;
    
    public LifeCycle() {}
    
    public LifeCycle(String f, String b) {
        foo = f;
        bar = b;
    }
    
    @OnLoad
    void onLoad() throws Exception {
        if (loadFail) throw new Exception();
        /*
         * cannot put field foo here because by the time
         * this method get called no field is initialized
         */
        Event.newEvent("foo", OnLoad.class);
    }
    
    @Loaded
    void loaded() throws Exception {
        if (loadedFail) throw new Exception();
        Event.newEvent(foo, Loaded.class);
    }
    
    @OnAdd
    protected void onAdd() throws Exception {
        if (addFail) throw new Exception();
        Event.newEvent(foo, OnAdd.class);
    }
    
    @OnAdd
    protected void onAdd2() {
        Event.newEvent(foo + "2", OnAdd.class);
    }

    @OnUpdate
    protected void onUpdate() throws Exception {
        if (updateFail) throw new Exception();
        Event.newEvent(foo, OnUpdate.class);
    }

    @OnUpdate
    protected void onUpdate2() {
        Event.newEvent(foo + "2", OnUpdate.class);
    }

    @OnDelete
    protected void onDelete() {
        if (deleteFail) throw new RuntimeException();
        Event.newEvent(foo, OnDelete.class);
    }

    @Added
    protected void added() {
        if (addedFail) throw new RuntimeException();
        Event.newEvent(foo, Added.class);
    }

    @Updated
    protected void updated() {
        if (updatedFail) throw new RuntimeException();
        Event.newEvent(foo, Updated.class);
    }

    @Deleted
    protected void deleted() {
        if (deletedFail) throw new RuntimeException();
        Event.newEvent(foo, Deleted.class);
    }
    
    private static Map<MorphiaQuery, Long> map0_ = new HashMap<MorphiaQuery, Long>();
    
    @SuppressWarnings("unused")
    @OnBatchDelete
    private static void onBatchDelete(MorphiaQuery q) {
        if (batchDeleteFail) throw new RuntimeException();
        long cnt = q.count();
        for (long l = 0; l < cnt; ++l) {
            Event.newEvent("foobar", OnBatchDelete.class);
        }
        map0_.put(q, cnt);
    }
    
    @SuppressWarnings("unused")
    @BatchDeleted
    private static void batchDeleted(MorphiaQuery q) {
        if (batchDeletedFail) throw new RuntimeException();
        long cnt = map0_.get(q);
        for (long l = 0; l < cnt; ++l) {
            Event.newEvent("foobar", BatchDeleted.class);
        }
    }
    
    @OnLoad
    @Loaded
    @OnAdd
    @OnUpdate
    @OnDelete
    @Added
    @Updated
    @Deleted
    protected void common() {
        Event.newEvent(null == foo ? "foo" : foo, Object.class);
    }
    
    @Entity
    @NoId
    public static class Child extends LifeCycle {
        public String fee;
        
        public Child() {}
        
        public Child(String f, String b, String fee) {
            super(f, b);
            this.fee = fee;
        }
        
        @OnLoad
        void onLoad() throws Exception {
            if (loadFail) throw new Exception();
            Event.newEvent("fee", OnLoad.class);
        }
        
        @Loaded
        void loaded() throws Exception {
            if (loadedFail) throw new Exception();
            Event.newEvent(fee, Loaded.class);
        }
        
        @SuppressWarnings("unused")
        @OnAdd
        private void myOnAdd() {
            Event.newEvent(fee, OnAdd.class);
        }
        
        @SuppressWarnings("unused")
        @Added
        private void myAdded() {
            Event.newEvent(fee, Added.class);
        }

        @SuppressWarnings("unused")
        @OnUpdate
        private void myOnUpdate() {
            Event.newEvent(fee, OnUpdate.class);
        }
        
        @SuppressWarnings("unused")
        @Updated
        private void myUpdated() {
            Event.newEvent(fee, Updated.class);
        }

        @SuppressWarnings("unused")
        @OnDelete
        private void myOnDelete() {
            Event.newEvent(fee, OnDelete.class);
        }
        
        @SuppressWarnings("unused")
        @Deleted
        private void myDeleted() {
            Event.newEvent(fee, Deleted.class);
        }

        private static Map<MorphiaQuery, Long> map0_ = new HashMap<MorphiaQuery, Long>();
        
        @SuppressWarnings("unused")
        @OnBatchDelete
        private static void onBatchDelete(MorphiaQuery q) {
            if (batchDeleteFail) throw new RuntimeException();
            long cnt = q.count();
            for (long l = 0; l < cnt; ++l) {
                Event.newEvent("456", OnBatchDelete.class);
            }
            map0_.put(q, cnt);
        }
        
        @SuppressWarnings("unused")
        @BatchDeleted
        private static void batchDeleted(MorphiaQuery q) {
            if (batchDeletedFail) throw new RuntimeException();
            long cnt = map0_.get(q);
            for (long l = 0; l < cnt; ++l) {
                Event.newEvent("456", BatchDeleted.class);
            }
        }
    }
}
