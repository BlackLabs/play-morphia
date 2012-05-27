package models;

import play.modules.morphia.Model;
import com.google.code.morphia.annotations.Entity;

@SuppressWarnings("serial")
@Entity
public class Child extends Model{
	
	public String childName;

}
