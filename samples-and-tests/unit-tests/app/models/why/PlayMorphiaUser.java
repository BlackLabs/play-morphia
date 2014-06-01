package models.why;

import play.modules.morphia.Model;

import org.mongodb.morphia.annotations.Entity;

@Entity("user")
public class PlayMorphiaUser extends Model {
    public String fName;
    public String lName;
    
    public PlayMorphiaUser(String firstName, String lastName) {
        fName = firstName;
        lName = lastName;
    }
}
