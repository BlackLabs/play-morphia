import java.io.File;
import java.util.List;

import models.User;

import org.junit.Before;
import org.junit.Test;

import play.modules.morphia.Blob;
import play.modules.morphia.MorphiaPlugin;
import play.test.UnitTest;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.MongoOptions;
import com.mongodb.gridfs.GridFSDBFile;

public class MongoOptionsTest extends UnitTest {

	private MongoOptions options;

	@Before
	public void before()
	{
        options = MorphiaPlugin.ds().getMongo().getMongoOptions();		
	}
	
    @Test
    public void testMongoOptionsCanBeSetInApplicationConf() {
        
        assertEquals(11, options.threadsAllowedToBlockForConnectionMultiplier);
        assertEquals(12, options.connectionsPerHost);
        assertEquals(true, options.slaveOk);
    }
}