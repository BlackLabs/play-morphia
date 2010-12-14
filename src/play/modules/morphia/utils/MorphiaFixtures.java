package play.modules.morphia.utils;

import java.util.List;

import play.Play;
import play.modules.morphia.Model;
import play.modules.morphia.MorphiaPlugin;
import play.test.Fixtures;

import com.google.code.morphia.Datastore;

public class MorphiaFixtures extends Fixtures {
    
	private static Datastore ds() {
        return MorphiaPlugin.ds();
    }
    
    public static void deleteAll() {
        Datastore ds = ds();
        for (Class<Model> clz: Play.classloader.getAssignableClasses(Model.class)) {
            ds.getCollection(clz).drop();
        }
    }
    
    public static void delete(Class<? extends Model> ... types) {
        for (Class<? extends Model> type: types) {
            ds().getCollection(type).drop();
        }
    }
    
    public static void delete(List<Class<? extends Model>> classes) {
        for (Class<? extends Model> type: classes) {
            ds().getCollection(type).drop();
        }
    }
    
    public static void deleteAllModels() {
        deleteAll();
    }
}
