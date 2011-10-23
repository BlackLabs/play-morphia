import models.Event;
import models.PureMorphiaModel;

import org.junit.Before;
import org.junit.Test;

import play.modules.morphia.Model.Added;
import play.modules.morphia.Model.OnAdd;
import play.modules.morphia.MorphiaPlugin;
import play.test.UnitTest;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.Morphia;
import com.google.code.morphia.annotations.PostPersist;
import com.google.code.morphia.annotations.PrePersist;
import com.google.code.morphia.annotations.PreSave;


public class PureMorphiaModelTest extends UnitTest {
    protected Morphia m = null;
    protected Datastore ds = null;
    @Before
    public void setup() {
        m = MorphiaPlugin.morphia();
        ds = MorphiaPlugin.ds();
        
        m.map(PureMorphiaModel.class);
        Event.reset();
    }
    
    @Test
    public void test() {
        PureMorphiaModel model = new PureMorphiaModel();
        model.fName = "green";
        model.lName = "luo";
        
        ds.save(model);
        
        t("foo", OnAdd.class, 0);
        t("foo", Added.class, 0);
        t("foo", PrePersist.class, 1);
        t("foo", PostPersist.class, 1);
        t("foo", PreSave.class, 1);
    }

    private void t(Object id, Class<?> type, int count) {
        assertTrue(Event.count(id, type) == count);
    }
}
