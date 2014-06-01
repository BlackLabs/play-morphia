package models;

import org.mongodb.morphia.annotations.Entity;
import play.modules.morphia.Model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 11/06/13
 * Time: 9:18 PM
 * To change this template use File | Settings | File Templates.
 */
@Entity("albumn")
public class Album extends Model {
    public String name;
    public Set<String> tags;

    public Album() {
        init();
    }

    public List<Photo> getPhotos() {
        return Photo.find("albumId", this.getId()).asList();
    }

    @Model.Loaded
    void init() {
        if (null == tags) {
            tags = new HashSet<String>();
        }
    }

}