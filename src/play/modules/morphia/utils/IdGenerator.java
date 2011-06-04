package play.modules.morphia.utils;

import org.bson.types.ObjectId;

import play.modules.morphia.Model;
import play.modules.morphia.MorphiaPlugin;
import play.modules.morphia.MorphiaPlugin.IdType;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.query.Query;
import com.google.code.morphia.query.UpdateOperations;
import com.google.code.morphia.utils.LongIdEntity.StoredId;


public class IdGenerator {
    public static Datastore ds() {
        return MorphiaPlugin.ds();
    }
    public static Object generateId(Model entity){
        IdType t = MorphiaPlugin.getIdType();
        switch (t) {
        case Long:
            return generateLongId(entity);
        case ObjectId:
            return generateObjectIdId(entity);
        default:
            throw new IllegalStateException("Shouldn't be here. Probably user entity does not override generateId() method for user annotated Id field.");
        }
    }
    
    public static <T extends Model> Long generateLongId(T entity){
        return generateLongId(entity.getClass());
    }
    
    public static <T extends Model> Long generateLongId(Class<T> clazz){
        String collName = ds().getCollection(clazz).getName();
        Query<StoredId> q = ds().find(StoredId.class, "_id", collName);
        UpdateOperations<StoredId> uOps = ds().createUpdateOperations(StoredId.class).inc("value");
        StoredId newId = ds().findAndModify(q, uOps);
        if (newId == null) {
            newId = new StoredId(collName);
            ds().save(newId);
        }
        return newId.getValue();
    }
    
    public static <T extends Model> ObjectId generateObjectIdId(T entity) {
        return new ObjectId();
    }
    
    public static <T extends Model> ObjectId generateObjectIdId(Class<T> clazz) {
        return new ObjectId();
    }
    
    public static String getIdTypeName() {
        IdType t = MorphiaPlugin.getIdType();
        switch (t) {
        case Long:
            return Long.class.getName();
        case ObjectId:
            return ObjectId.class.getName();
        default:
            throw new IllegalStateException("How can i get here???");
        }
    }
    
    public static Object processId(Object id) {
        IdType t = MorphiaPlugin.getIdType();
        switch (t) {
        case Long:
            return processLongId(id);
        case ObjectId:
            return processObjectId(id);
        default:
            return id;
        }
    }
    
    public static ObjectId processObjectId(Object id) {
        if (id instanceof ObjectId) return (ObjectId)id;
        return null == id ? null : new ObjectId(id.toString());
    }
    
    public static Long processLongId(Object id) {
        if (id instanceof Long) return (Long)id;
        return null == id ? null : Long.parseLong(id.toString());
    }
}
