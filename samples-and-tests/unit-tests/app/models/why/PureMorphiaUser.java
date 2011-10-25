package models.why;

import org.bson.types.ObjectId;

import com.google.code.morphia.Morphia;
import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Id;

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
