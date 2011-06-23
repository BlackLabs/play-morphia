package models;

import play.modules.morphia.Blob;
import play.modules.morphia.Model;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Transient;

@Entity
public class User extends Model {

    public String name;
    public Blob photo;
}
