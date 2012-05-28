package models;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Reference;
import play.modules.morphia.Model;


@SuppressWarnings("serial")
@Entity
public class Parent extends Model {

	public String parentName;

	@Reference
	public Child child;

}
