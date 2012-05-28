package models;


import com.google.code.morphia.annotations.Entity;
import play.modules.morphia.Model;

@SuppressWarnings("serial")
@Entity
public class Child extends Model {

	public String childName;

}
