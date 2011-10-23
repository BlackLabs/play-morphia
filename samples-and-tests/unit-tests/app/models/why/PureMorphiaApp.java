package models.why;

import java.util.List;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.Morphia;
import com.mongodb.Mongo;

public class PureMorphiaApp {

    private static Datastore getDatastore() throws Exception {
        Morphia morphia = new Morphia();
        PureMorphiaUser.ensureMapped(morphia);
        Mongo mongo = new Mongo();
        return morphia.createDatastore(mongo, "mydatabase");
    }

    public static void crud() throws Exception {
        Datastore ds = getDatastore();
        // create
        PureMorphiaUser user = new PureMorphiaUser("John", "Smith");
        ds.save(user);
        // read
        PureMorphiaUser user2 = ds.get(PureMorphiaUser.class, user.id);
        // update
        user2.fName = "Tom";
        ds.save(user2);
        // delete
        ds.delete(user2);
    }
    
    public static void query() throws Exception {
        Datastore ds = getDatastore();
        // find
        List<PureMorphiaUser> users = ds.createQuery(PureMorphiaUser.class)
                .filter("fName", "John").filter("lName", "Smith").asList();
    }
    
}
