package models;

import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Reference;
import play.modules.morphia.Model;


@SuppressWarnings("serial")
@Entity
public class Parent extends Model {

	public String parentName;

	@Reference
	public Child child;

}
