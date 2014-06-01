package play.modules.morphia;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;
import org.osgl.exception.UnexpectedIOException;
import org.osgl.storage.ISObject;
import org.osgl.storage.IStorageService;
import org.osgl.storage.KeyGenerator;
import org.osgl.storage.impl.SObject;
import org.osgl.storage.impl.StorageServiceBase;
import org.osgl.util.C;
import org.osgl.util.IO;
import org.osgl.util.S;
import play.Logger;
import play.mvc.Router;

import java.util.Map;

/**
 * A storage service built on top of mongodb GridFS
 */
public class GridFSStorageService extends StorageServiceBase implements IStorageService {

    public GridFSStorageService(KeyGenerator keygen) {
        super(keygen);
    }

    @Override
    public ISObject get(String key) {
        GridFSDBFile file = findFile(key);
        if (null == file) {
            // try legacy
            file = findFile(BlobStorageService.getLegacyKey(key));
            if (null != file) {
                Logger.warn("You have legacy blob data, please consider migrating them to new version");
            }
        }
        if (null == file) {
            return null;
        }
        ISObject sobj = SObject.of(key, IO.readContent(file.getInputStream()));
        String fn = file.getFilename();
        if (S.empty(fn)) {
            fn = S.random(8);
        }
        String type = file.getContentType();
        if (S.empty(type)) {
            type = "application/octet-stream";
        }
        return sobj.setAttribute(Blob.FILENAME, fn)
            .setAttribute(Blob.CONTENT_TYPE, type);
    }

    @Override
    public void put(String key, ISObject stuff) throws UnexpectedIOException {
        GridFS gfs = gfs();
        gfs.remove(new BasicDBObject("name", key));
        GridFSInputFile inputFile = gfs.createFile(stuff.asByteArray());
        inputFile.setContentType(stuff.getAttribute(Blob.CONTENT_TYPE));
        inputFile.put("name", key);
        inputFile.put("filename", stuff.getAttribute(Blob.FILENAME));
        inputFile.save();
    }

    @Override
    public void remove(String key) {
        gfs().remove(new BasicDBObject("name", key));
        // the following line is to make sure the old blob data get removed
        // the line will be removed in the next release
        BlobStorageService.removeLater(BlobStorageService.getLegacyKey(key), this);
    }

    @Override
    public String getUrl(String key) {
        Map<String, Object> params = C.newMap("key", ((Object)key));
        return Router.getFullUrl("controllers.BlobController.view", params);
    }

    @Override
    public ISObject loadContent(ISObject sobj) {
        return get(sobj.getKey());
    }

    private static GridFSDBFile findFile(String key) {
        DBObject queryObj = new BasicDBObject("name", key);
        return gfs().findOne(queryObj);
    }
    
    private static GridFS gfs() {
        return MorphiaPlugin.gridFs();
    }
    
}
