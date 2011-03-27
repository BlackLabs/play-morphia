package models;

import com.google.code.morphia.annotations.Embedded;
import play.modules.morphia.Model;

@Embedded
public class Child extends Model {

    public Integer age;

}
