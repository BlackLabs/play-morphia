package models;

import play.modules.morphia.Blob;
import play.modules.morphia.Model;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Transient;

@Entity
public class User extends Model {

    public String name;
    // TODO: You may not implement your own getters for blobs for now. They will be overwritten. Play 1.3 will support this.
    public Blob photo;
}
