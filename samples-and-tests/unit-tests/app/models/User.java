package models;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import play.Logger;
import play.modules.morphia.Blob;
import play.modules.morphia.Model;
import play.modules.morphia.MorphiaPlugin;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Transient;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.gridfs.GridFSDBFile;

@Entity
public class User extends Model {

    public String name;

    @Transient
    public Blob photo;

    // TODO Make this byte code enhancing for all Blob typed fields with @Transient annotation
    // This implements some sort of lazy loading feature...
    public Blob getPhoto() {
        if (photo == null && !isNew()) {
            Blob b = new Blob("User_photo_" + getId().toString());
            photo = b.exists() ? b : null;
        }
        return photo;
    }
}
