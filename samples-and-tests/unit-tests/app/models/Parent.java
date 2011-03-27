package models;

import com.google.code.morphia.annotations.Embedded;
import com.google.code.morphia.annotations.Entity;
import play.data.validation.Valid;
import play.modules.morphia.Model;

@Entity(noClassnameStored = true)
public class Parent extends Model {

    public String name;
    @Valid
    public Child child;

}
