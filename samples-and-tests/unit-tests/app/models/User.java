package models;

import play.modules.morphia.Blob;
import play.modules.morphia.Model;

import com.google.code.morphia.annotations.Entity;

@SuppressWarnings("serial")
@Entity(noClassnameStored = true)
public class User extends PhotoHolder {

    @Column("nm")
    public String name;
    public String tag = "testing";

    public User() {}

    public User(String name, String tag) {
        this.name = name;
        this.tag = tag;
    }

}
