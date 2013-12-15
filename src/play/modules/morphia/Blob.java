package play.modules.morphia;

import org.bson.types.ObjectId;
import org.osgl.exception.UnexpectedIOException;
import org.osgl.storage.ISObject;
import org.osgl.storage.IStorageService;
import org.osgl.storage.impl.SObject;
import org.osgl.util.C;
import org.osgl.util.E;
import org.osgl.util.S;
import play.cache.Cache;
import play.db.Model.BinaryField;
import play.libs.F;
import play.modules.morphia.utils.MimeTypes;
import play.mvc.Router;

import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class Blob implements BinaryField, Serializable {

    private static class LazyLoadSObject implements ISObject {
        private ISObject sobj;

        LazyLoadSObject(ISObject sobj) {
            E.NPE(sobj);
            this.sobj = sobj;
        }
        
        @Override
        public String getKey() {
            return sobj.getKey();
        }

        @Override
        public long getLength() {
            return sobj.getLength();
        }

        @Override
        public String getAttribute(String key) {
            return sobj.getKey();
        }

        @Override
        public ISObject setAttribute(String key, String val) {
            return sobj.setAttribute(key, val);
        }

        @Override
        public boolean hasAttribute() {
            return sobj.hasAttribute();
        }

        @Override
        public Map<String, String> getAttributes() {
            return sobj.getAttributes();
        }

        @Override
        public boolean isEmpty() {
            return sobj.isEmpty();
        }

        @Override
        public boolean isValid() {
            return sobj.isValid();
        }

        @Override
        public Throwable getException() {
            return sobj.getException();
        }

        @Override
        public File asFile() throws UnexpectedIOException {
            return sobj.asFile();
        }

        @Override
        public String asString() throws UnexpectedIOException {
            return sobj.asString();
        }

        @Override
        public byte[] asByteArray() throws UnexpectedIOException {
            return sobj.asByteArray();
        }

        @Override
        public InputStream asInputStream() throws UnexpectedIOException {
            return sobj.asInputStream();
        }
    }

    public static final String CONTENT_TYPE = "content-type";
    public static final String FILENAME = "filename";
    
    public static final String TMP_ID_SUFFIX = "__TMPBLOB__";

    private static final String NULL_KEY = "BLOB_NULL_ID";

    private ISObject sobj;
    private BlobStorageService ss;
    
    private String tmpId;

    public Blob(InputStream is, String type) {
        set(is, type);
    }

    public Blob(File inputFile, String type) {
        sobj = SObject.valueOf(NULL_KEY, inputFile);
        if (S.empty(type)) {
            type = MimeTypes.probe(inputFile);
        }
        if (S.notEmpty(type)) {
            type(type);
        }
        sobj.setAttribute(FILENAME, inputFile.getName());
    }

    public Blob(File inputFile) {
        this(inputFile, null);
    }

    public Blob(ISObject sobj, BlobStorageService ss) {
        E.NPE(sobj, ss);
        
        this.sobj = sobj;
        this.ss = ss;

        if (sobj instanceof LazyLoadSObject) {
            final F.Promise<ISObject> p = ss.loadLater(sobj.getKey());
            final Blob me = this;
            new play.jobs.Job (){
                @Override
                public void doJob() throws Exception {
                    ISObject sobj = p.get();
                    if (null != sobj) {
                        me.sobj = sobj;
                    }
                }
            }.now();
        }
    }
    
    private void type(String type) {
        sobj.setAttribute(CONTENT_TYPE, type);
    }
    
    public String getKey() {
        return sobj.getKey();
    }

    public static Blob load(String key, BlobStorageService ss) {
        ISObject sobj = ss.get(key);
        if (null == sobj) {
            // might be null because we are using
            // async method to put the object
            // so create a proxy sobj
            sobj = new LazyLoadSObject(SObject.valueOf(key, ""));
        }
        return new Blob(sobj, ss);
    }

    @Override
    public InputStream get() {
        return sobj.asInputStream();
    }
    
    public InputStream forceGet() {
        if (sobj.isEmpty()) {
            sobj = ss.forceGet(sobj.getKey());
        }
        return sobj.asInputStream();
    }

    @Override
    public void set(InputStream is, String type) {
        if (exists()) {
            BlobStorageService.removeLater(sobj.getKey(), ss);
        }
        sobj = SObject.valueOf(NULL_KEY, is);
        type(type);
    }

    @Override
    public long length() {
        return sobj.getLength();
    }

    @Override
    public String type() {
        return sobj.getAttribute(CONTENT_TYPE);
    }
    
    public String fileName() {
        return sobj.getAttribute(FILENAME);
    }

    @Override
    public boolean exists() {
        return null != sobj && S.neq(NULL_KEY, sobj.getKey());
    }

    public void delete() {
        if (exists()) {
            ss.remove(sobj.getKey());
        }
    }

    public static void delete(String key, IStorageService ss) {
        ss.remove(key);
    }

    @Override
    public String toString() {
        return S.fmt("BLOB://[%s]/%s", sobj.getKey(), sobj.getAttribute(FILENAME));
    }

    public String createKey(String hostId, String fieldName, BlobStorageService ss) {
        E.invalidStateIf(exists(), "cannot create key for existing blob");
        return ss.getKey(hostId, fieldName, this);
    }

    public void batchDelete(Collection<String> keys) {
        Set<String> set = C.set(keys);
        for (String key : set) {
            ss.remove(key);
        }
    }

    public Blob save(String key, BlobStorageService ss) {
        if (exists()) {
            if (S.neq(sobj.getKey(), key)) {
                throw E.unexpected("Blob key doesn't match");
            } else {
                // remove previous version
                ss.remove(key);
            }
        } else {
            // this replace the NULL_KEY with the real key
            sobj = SObject.valueOf(key, sobj);
        }
        ss.put(key, sobj);
        this.ss = ss;
        return this;
    }
    
    public String getUrl() {
        if (!exists()) {
            if (null == tmpId) {
                tmpId = new ObjectId().toString() + TMP_ID_SUFFIX;
            }
            Cache.set(tmpId, this, "10min");
            Map<String, Object> params = C.newMap("key", tmpId);
            return Router.getFullUrl("controllers.BlobController.view", params);
        } else {
            String key = getKey();
            if (Cache.get(key) != null) {
                Map<String, Object> params = C.newMap("key", key);
                return Router.getFullUrl("controllers.BlobController.view", params);
            } else {
                return ss.getUrl(this);
            }
        }
    }
}
