import models.User;

import org.junit.Test;

import play.test.UnitTest;

import com.mongodb.DBCollection;


public class GetCollectionInterfaceTest extends UnitTest {
    @Test
    public void test() {
        DBCollection col = User.col();
        assertEquals(col.count(), User.count());
        assertEquals(col.getName(), "User");
    }
}
