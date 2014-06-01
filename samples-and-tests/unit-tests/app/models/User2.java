package models;

import play.modules.morphia.Blob;
import play.modules.morphia.Model;

import org.mongodb.morphia.annotations.Entity;

@SuppressWarnings("serial")
@Entity(value="User", noClassnameStored = true)
public class User2 extends Model {

    public String nm;
    public Blob photo;
    public String tag = "testing";
    
}
