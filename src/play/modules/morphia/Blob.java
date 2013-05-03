package play.modules.morphia;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.bson.types.ObjectId;
import play.db.Model.BinaryField;

import javax.activation.MimetypesFileTypeMap;
import java.io.*;

public class Blob implements BinaryField {

    private GridFSDBFile file;
    
    private byte[] buf = null;
    
    private String type;
    
    private String name;

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
        file = findFile(id);
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
        name = file.getName();
        this.type = type;
    }

    @Override
    public void set(InputStream is, String type) {
        try {
            buf = IOUtils.toByteArray(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        name = RandomStringUtils.randomAlphanumeric(10);
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
    
    public Blob save(String name) {
        if (!isNew()) {
            return this;
        }
        if (null != buf) {
            GridFSInputFile inputFile = MorphiaPlugin.gridFs().createFile(buf);
            inputFile.setContentType(type);
            inputFile.put("name", name);
            inputFile.put("filename", this.name);
            inputFile.save();
            file = MorphiaPlugin.gridFs().findOne(new ObjectId(inputFile.getId().toString()));
        }
        return this;
    }
}
