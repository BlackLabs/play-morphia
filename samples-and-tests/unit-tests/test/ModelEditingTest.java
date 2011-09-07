import java.util.HashMap;

import models.edit.CustomKeyChild;
import models.edit.DefaultKeyChild;
import models.edit.Parent;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import play.test.UnitTest;

/**
 * The model editing test on references.
 *
 */
public class ModelEditingTest extends UnitTest
{
    /**
     * Clear the models.
     */
    @Before
    public void setup()
    {
        Parent.deleteAll();
        CustomKeyChild.deleteAll();
        DefaultKeyChild.deleteAll();
    }

    /**
     * Test single relation with custom key.
     */
    @Test
    public void testSingleRelationWithCustomKey()
    {
        CustomKeyChild child1 = new CustomKeyChild();
        child1.key = "child1";
        child1.name = "Child One";
        child1.save();

        HashMap<String, String[]> params = new HashMap<String, String[]>();
        params.put("parent.customKeyChild.key", new String[] { "child1" });
        Parent parent = new Parent();
        parent = parent.edit("parent", params);
        
        Assert.assertNotNull(parent.customKeyChild);
        Assert.assertEquals(child1.name, parent.customKeyChild.name);
    }
    
    /**
     * Test single relation with default key.
     */
    @Test
    public void testSingleRelationWithDefaultKey()
    {
        DefaultKeyChild child1 = new DefaultKeyChild();
        child1.name = "Child One";
        child1.save();

        HashMap<String, String[]> params = new HashMap<String, String[]>();
        params.put("parent.defaultKeyChild._id", new String[] { child1._key().toString() });
        Parent parent = new Parent();
        parent = parent.edit("parent", params);
        
        Assert.assertNotNull(parent.defaultKeyChild);
        Assert.assertEquals(child1.name, parent.defaultKeyChild.name);
    }
    
    /**
     * Test multiple relation with custom key.
     */
    @Test
    public void testMultipleRelationWithCustomKey()
    {
        CustomKeyChild child1 = new CustomKeyChild();
        child1.key = "child1";
        child1.name = "Child One";
        child1.save();

        HashMap<String, String[]> params = new HashMap<String, String[]>();
        params.put("parent.customKeyChildren.key", new String[] { "child1" });
        Parent parent = new Parent();
        parent = parent.edit("parent", params);
        
        Assert.assertNotNull(parent.customKeyChildren);
        Assert.assertEquals(1, parent.customKeyChildren.size());
        Assert.assertEquals(child1.name, parent.customKeyChildren.get(0).name);
    }
    
    /**
     * Test multiple relation with default key.
     */
    @Test
    public void testMultipleRelationWithDefaultKey()
    {
        DefaultKeyChild child1 = new DefaultKeyChild();
        child1.name = "Child One";
        child1.save();

        HashMap<String, String[]> params = new HashMap<String, String[]>();
        params.put("parent.defaultKeyChildren._id", new String[] { child1._key().toString() });
        Parent parent = new Parent();
        parent = parent.edit("parent", params);
        
        Assert.assertNotNull(parent.defaultKeyChildren);
        Assert.assertEquals(1, parent.defaultKeyChildren.size());
        Assert.assertEquals(child1.name, parent.defaultKeyChildren.get(0).name);
    }
}
