package play.modules.morphia.utils;

import org.bson.types.ObjectId;

import play.modules.morphia.Model;
import play.modules.morphia.MorphiaPlugin;
import play.modules.morphia.MorphiaPlugin.IdType;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.query.Query;
import com.google.code.morphia.query.UpdateOperations;
import com.google.code.morphia.utils.LongIdEntity.StoredId;
import play.modules.morphia.Seq;


public class IdGenerator {
    public static Datastore ds() {
        return MorphiaPlugin.ds();
    }
    public static Object generateId(Model entity){
        IdType t = MorphiaPlugin.getIdType();
        switch (t) {
        case STRING:
            return generateStringId(entity);
        case LONG:
            return generateLongId(entity);
        case OBJECT_ID:
            return generateObjectIdId(entity);
        default:
            throw new IllegalStateException("Shouldn't be here. Probably user entity does not override generateId() method for user annotated Id field.");
        }
    }

    public static <T extends Model> String generateStringId(T entity){
        return generateStringId(entity.getClass());
    }

    public static <T extends Model> String generateStringId(Class<T> clazz) {
        return MorphiaPlugin.generateStringId();
    }

    public static <T extends Model> Long generateLongId(T entity){
        return generateLongId(entity.getClass());
    }

    public static <T extends Model> Long generateLongId(Class<T> clazz){
        return Seq.nextValue(clazz);
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
        case STRING:
            return String.class.getName();
        case LONG:
            return Long.class.getName();
        case OBJECT_ID:
            return ObjectId.class.getName();
        default:
            throw new IllegalStateException("How can i get here???");
        }
    }

    public static Object processId(Object id) {
        IdType t = MorphiaPlugin.getIdType();
        switch (t) {
        case STRING:
            return processStringId(id);
        case LONG:
            return processLongId(id);
        case OBJECT_ID:
            return processObjectId(id);
        default:
            return id;
        }
    }

    public static String processStringId(Object id) {
        return null == id ? null : id.toString();
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
