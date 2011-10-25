package models;

import org.bson.types.ObjectId;

import play.modules.morphia.Model;

import com.google.code.morphia.annotations.Id;


@SuppressWarnings("serial")
public abstract class Base extends Model {
	@Id
	public ObjectId _id;
	
	protected Base() {_id = new ObjectId();}
	
	protected Base(ObjectId id) {_id = id;}
	
	@Override public Object getId() {return _id;}
	
	@Override protected void setId_(Object id) {_id = (ObjectId)processId_(id);}
	
	protected static Object processId_(Object id) {return (id instanceof ObjectId) ? id : new ObjectId(id.toString());}
}
