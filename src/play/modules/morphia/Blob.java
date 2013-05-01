package play.modules.morphia;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;
import org.apache.commons.lang.RandomStringUtils;
import org.bson.types.ObjectId;
import play.Logger;
import play.db.Model.BinaryField;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class Blob implements BinaryField {

    private GridFSDBFile file;
    
    private InputStream is;
    
    private File file0;
    
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
        } else if (null != is) {
            return is;
        } else if (null != file0) {
            try {
                return new FileInputStream(file0);
            } catch (IOException e) {
                throw new RuntimeException("cannot get input stream from " + file0.getAbsolutePath());
            }
        }
        return null;
    }
    
    public void set(File file, String type) {
        this.file0 = file;
        this.type = type;
    }

    @Override
    public void set(InputStream is, String type) {
        this.is = is;
        this.type = type;
    }

    @Override
    public long length() {
        if (null != file0) return file0.length();
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
        } else if (null != file0) { 
            return "BLOB://" + file0.getPath();
        } else if (null != is) {
            return "BLOB://" + is;
        }
        return "BLOB://null";
    }
    
    public boolean isNew() {
        return file == null;
    }
    
    public void save() {
        if (null != is) {
            String rand = RandomStringUtils.randomAlphanumeric(10);
            GridFSInputFile inputFile = MorphiaPlugin.gridFs().createFile(is);
            inputFile.setContentType(type);
            inputFile.put("name", rand);
            inputFile.save();
            file = MorphiaPlugin.gridFs().findOne(new ObjectId(inputFile.getId().toString()));
        } else if (null != file0) {
            if (!file0.exists()) {
                Logger.warn("File not exists: %s", file0);
                return;
            }
            try {
                GridFSInputFile inputFile = MorphiaPlugin.gridFs().createFile(file0);
                inputFile.setContentType(type);
                inputFile.save();
                this.file = MorphiaPlugin.gridFs().findOne(new ObjectId(inputFile.getId().toString()));
            } catch (IOException e){
                throw new RuntimeException(e);
            }
        }
    }
}
