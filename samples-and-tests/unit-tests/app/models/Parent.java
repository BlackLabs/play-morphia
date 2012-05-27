package models;

import play.modules.morphia.Model;
import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Reference;

@SuppressWarnings("serial")
@Entity
public class Parent extends Model{
	
	public String parentName;
	
	@Reference
	public Child child;

}
