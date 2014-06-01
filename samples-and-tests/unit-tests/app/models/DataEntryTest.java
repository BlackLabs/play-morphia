package models;

import org.mongodb.morphia.query.CriteriaContainer;
import org.junit.Before;
import org.junit.Test;
import play.Logger;
import play.modules.morphia.Model;
import play.test.UnitTest;

import java.util.List;

public class DataEntryTest extends UnitTest {
    
    @Before
    public void setup() {
        DataEntry.prepareData();
        q = DataEntry.q();
    }
    
    protected long c;
    protected Model.MorphiaQuery q;
    
    protected void eq(Object a, Object b) {
        assertEquals(a, b);
    }
    
//    @Test
//    public void facebookImage() {
//        c = q.filter("data.key", "facebookImage").count();
//        eq(3l, c);
//    }
//    
//    
//    @Test
//    public void uploadImage() {
//        c = q.filter("data.key", "upload").count();
//        eq(3l, c);
//    }
//    
//    @Test
//    public void allImage() {
//        q.or(q.criteria("data.key").equal("facebookImage"), q.criteria("data.key").equal("upload"));
//        eq(6l, q.count());
//    }
//
//    @Test
//    public void approved() {
//        c = q.filter("approved", true).count();
//        eq(3l, c);
//    }
//    
//
//    @Test
//    public void notModerated() {
//        c = q.filter("moderated", false).count();
//        eq(3l, c);
//    }
//    
//
//    @Test
//    public void rejected() {
//        q.filter("approved", false).filter("moderated", true);
//        eq(3l, q.count());
//    }
//
//
//    @Test
//    public void notModeratedOrApproved() {
//        q.or(q.criteria("approved").equal(true), q.criteria("moderated").equal(false));
//        eq(6l, q.count());
//    }
    
    @Test
    public void notModeratedOrApprovedWithAllImage() {
        c = DataEntry.Q.BY_DATE.find(false, true).size();
        assertEquals(4, c);
    }
    
    
}
