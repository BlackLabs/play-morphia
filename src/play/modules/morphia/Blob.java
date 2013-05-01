package play.modules.morphia;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.bson.types.ObjectId;
import play.Logger;
import play.db.Model.BinaryField;

import javax.activation.MimetypesFileTypeMap;
import java.io.*;

public class Blob implements BinaryField {

    private GridFSDBFile file;
    
    private byte[] buf = null;
    
    private String type;

    public Blob() {}

    public Blob(InputStream is, String type) {
        this();
        set(is, type);
    }

    public Blob(File inputFile, String type) {
        this();
        set(inputFile, type);
    }
    
    public Blob(File inputFile) {
        this(inputFile, new MimetypesFileTypeMap().getContentType(inputFile));
    }

    public Blob(String id) {
        DBObject queryObj = new BasicDBObject("name", id);
        file = MorphiaPlugin.gridFs().findOne(queryObj);
    }
    
    public void delete() {
        if (null == file) return;
        MorphiaPlugin.gridFs().remove((ObjectId)file.getId());
    }
    
    public static GridFSDBFile findFile(String name) {
        DBObject queryObj = new BasicDBObject("name", name);
        return MorphiaPlugin.gridFs().findOne(queryObj);
    }

    @Override
    public InputStream get() {
        if (null != file) {
            return file != null ? file.getInputStream() : null;
        } else if (null != buf) {
            return new ByteArrayInputStream(buf);
        } else {
            return null;
        }
    }
    
    public void set(File file, String type) {
        try {
            buf = IOUtils.toByteArray(new FileInputStream(file));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.type = type;
    }

    @Override
    public void set(InputStream is, String type) {
        try {
            buf = IOUtils.toByteArray(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.type = type;
    }

    @Override
    public long length() {
        if (null != buf) return buf.length;
        else if (null != file) return file == null ? 0 : file.getLength();
        else return 0;
    }

    @Override
    public String type() {
        if (null != type) return type;
        else if (null != file) return file.getContentType();
        else return null;
    }

    @Override
    public boolean exists() {
        return file != null && file.getId() != null;
    }
    
    public static void delete(String name) {
        MorphiaPlugin.gridFs().remove(new BasicDBObject("name", name));
    }

    public GridFSDBFile getGridFSFile() {
        return file;
    }

    @Override
    public String toString() {
        if (file != null) {
            return "BLOB://" + file.getId() + "/" + file.getFilename();
        } else if (null != buf) { 
            return "BLOB://[...]";
        } else {
            return "BLOB://null";
        }
    }
    
    public boolean isNew() {
        return file == null;
    }
    
    public void save() {
        if (!isNew()) {
            return;
        }
        if (null != buf) {
            String rand = RandomStringUtils.randomAlphanumeric(10);
            GridFSInputFile inputFile = MorphiaPlugin.gridFs().createFile(buf);
            inputFile.setContentType(type);
            inputFile.put("name", rand);
            inputFile.save();
            file = MorphiaPlugin.gridFs().findOne(new ObjectId(inputFile.getId().toString()));
        }
    }
}
