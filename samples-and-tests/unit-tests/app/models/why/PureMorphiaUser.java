package models.why;

import org.bson.types.ObjectId;

import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Entity("user")
public class PureMorphiaUser {
    @Id
    public ObjectId id;
    public String fName;
    public String lName;
    
    public PureMorphiaUser(String firstName, String lastName) {
        fName = firstName;
        lName = lastName;
    }
    
    private static boolean mapped = false;
    public static synchronized void ensureMapped(Morphia morphia) {
        if (!mapped) {
            morphia.map(PureMorphiaUser.class);
            mapped = true;
        }
    }
}
