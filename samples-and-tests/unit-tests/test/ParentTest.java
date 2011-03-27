
import models.Child;
import models.Parent;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import play.modules.morphia.utils.MorphiaFixtures;
import play.test.UnitTest;

public class ParentTest extends UnitTest  {

    @Before
    public void setUp() {
        // following line does not work with beta 2, but fixed in beta 3
//        Parent.deleteAll();
        Parent.deleteAll();
    }


    @Test
    public void creation() {
        Assert.assertEquals("Should be Zero Parents", 0, Parent.count());
        Parent paul = new Parent();
        paul.name = "Paul";
        Assert.assertTrue("Paul should save ok", paul.validateAndSave());
    }


    @Test
    public void creationWithChild() {
        Assert.assertEquals("Should be Zero Parents", 0, Parent.count());
        Parent gary = new Parent();
        gary.name = "Gary";
//        gary.child = new Child();
//        gary.child.age = 2;
        // fails to add embedded
        Child kid = new Child();
        kid.age = 2;
        gary.child = kid;
        Assert.assertTrue("Gary should save ok with a kid", gary.validateAndSave());
    }

    @Test
    public void morphiaLoad() {
        MorphiaFixtures.load("../test/parent-data.yml");
        Assert.assertEquals("Should be One Parent", 1, Parent.count());
        
    }

}
