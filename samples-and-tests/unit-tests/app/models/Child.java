package models;

import com.google.code.morphia.annotations.Embedded;
import com.google.code.morphia.annotations.Entity;
import play.modules.morphia.Model;

@Embedded
@Entity
public class Child extends Model {

    public Integer age;

}
