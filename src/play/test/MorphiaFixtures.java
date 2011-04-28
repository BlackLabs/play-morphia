package play.test;

import java.util.ArrayList;
import java.util.List;

import play.Play;
import play.classloading.ApplicationClasses;
import play.modules.morphia.Model;
import play.modules.morphia.MorphiaPlugin;

import com.google.code.morphia.Datastore;

public class MorphiaFixtures extends Fixtures {
    
	private static Datastore ds() {
        return MorphiaPlugin.ds();
    }
    
    public static void deleteDatabase() {
    	idCache.clear();
        Datastore ds = ds();
        for (Class<Model> clz: Play.classloader.getAssignableClasses(Model.class)) {
            ds.getCollection(clz).drop();
        }
    }
    
    public static void delete(Class<? extends Model> ... types) {
    	idCache.clear();
        for (Class<? extends Model> type: types) {
            ds().getCollection(type).drop();
        }
    }
    
    public static void delete(List<Class<? extends Model>> classes) {
    	idCache.clear();
        for (Class<? extends Model> type: classes) {
            ds().getCollection(type).drop();
        }
    }
    
    @SuppressWarnings("unchecked")
    public static void deleteAllModels() {
        List<Class<? extends Model>> mongoClasses = new ArrayList<Class<? extends Model>>();
        for (ApplicationClasses.ApplicationClass c : Play.classes.getAssignableClasses(play.db.Model.class)) {
        	Class<?> jc = c.javaClass;
        	mongoClasses.add((Class<? extends Model>)jc);
        }
        MorphiaFixtures.delete(mongoClasses);
    }
}
