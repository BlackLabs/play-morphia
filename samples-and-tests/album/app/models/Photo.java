package models;

import org.mongodb.morphia.annotations.Entity;
import play.modules.morphia.Blob;
import play.modules.morphia.Model;

import java.util.HashSet;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 11/06/13
 * Time: 9:19 PM
 * To change this template use File | Settings | File Templates.
 */
@Entity("photo")
public class Photo extends Model {
    public String albumId;
    public String desc;
    
    public Blob blob;
    public Set<String> tags;
    
    public Photo(String albumId) {
        init();
        this.albumId = albumId;
    }

    @Model.Loaded
    void init() {
        if (null == tags) {
            tags = new HashSet<String>();
        }
    }
    
    public String getUrl() {
        return null == blob ? null: blob.getUrl();
        //return null;
    }
}

