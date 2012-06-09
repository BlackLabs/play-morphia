package play.modules.morphia;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang.RandomStringUtils;
import org.bson.types.ObjectId;

import play.Logger;
import play.db.Model.BinaryField;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;

import javax.activation.MimetypesFileTypeMap;

public class Blob implements BinaryField {

    private GridFSDBFile file;

    public Blob() {}

    public Blob(InputStream is, String type) {
        this();
        set(is, type);
    }

    public Blob(File inputFile, String type) {
        this();
        try {
            set(inputFile, type);
        } catch (IOException e) {
            Logger.debug("File not found: %s (%s)", inputFile.getAbsolutePath(), e.getMessage());
        }
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
        return file != null ? file.getInputStream() : null;
    }
    
    public void set(File file, String type) throws IOException {
        if (!file.exists()) {
            Logger.warn("File not exists: %s", file);
            return;
        }
        GridFSInputFile inputFile = MorphiaPlugin.gridFs().createFile(file);
        inputFile.setContentType(type);
        inputFile.save();
        this.file = MorphiaPlugin.gridFs().findOne(new ObjectId(inputFile.getId().toString()));
    }
    

    @Override
    public void set(InputStream is, String type) {
        String rand = RandomStringUtils.randomAlphanumeric(10);
        GridFSInputFile inputFile = MorphiaPlugin.gridFs().createFile(is);
        inputFile.setContentType(type);
        inputFile.put("name", rand);
        inputFile.save();
        file = MorphiaPlugin.gridFs().findOne(new ObjectId(inputFile.getId().toString()));
    }

    @Override
    public long length() {
        return file == null ? 0 : file.getLength();
    }

    @Override
    public String type() {
        return file.getContentType();
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
            return file.getId() + "/" + file.getFilename();
        }
        return null;
    }
}
