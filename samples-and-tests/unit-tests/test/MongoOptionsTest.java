import java.io.File;
import java.util.List;

import com.mongodb.MongoClientOptions;
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

	private MongoClientOptions options;

	@Before
	public void before()
	{
        options = MorphiaPlugin.ds().getMongo().getMongoClientOptions();
	}
	
    @Test
    public void testMongoOptionsCanBeSetInApplicationConf() {
        
        assertEquals(11, options.getThreadsAllowedToBlockForConnectionMultiplier());
        assertEquals(12, options.getConnectionsPerHost());
    }
}
