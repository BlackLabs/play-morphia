package play.modules.morphia;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFSDBFile;

import play.Logger;
import play.db.Model.BinaryField;

public class Blob implements BinaryField {

    private File file;
    private String type;

    public Blob() {}

    public Blob(InputStream is, String type) {
        this();
        set(is, type);
    }

    public Blob(String id) {
        DBObject queryObj = new BasicDBObject("name", id);
        GridFSDBFile gridFSDBFile = MorphiaPlugin.gridFs().findOne(queryObj);
        if (gridFSDBFile != null) {
            set(gridFSDBFile.getInputStream(), gridFSDBFile.getContentType());
        }
    }

    @Override
    public InputStream get() {
        try {
            return file != null && file.exists() ? new FileInputStream(file) : null;
        } catch (FileNotFoundException e) {
            Logger.error("File not found though expected", e.getMessage());
        }
        return null;
    }

    @Override
    public void set(InputStream is, String type) {
        try {
            file = File.createTempFile("morphia", "tmp");
            IOUtils.copy(is, new FileOutputStream(file));
            this.type = type;
        } catch (IOException e) {
            Logger.error(e, "Problem creating temp file");
        }
    }

    @Override
    public long length() {
        return file == null ? 0 : file.length();
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public boolean exists() {
        return file != null && file.exists();
    }
}
