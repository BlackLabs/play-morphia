package models.why;

import play.modules.morphia.Model;

import com.google.code.morphia.annotations.Entity;

@Entity("user")
public class PlayMorphiaUser extends Model {
    public String fName;
    public String lName;
    
    public PlayMorphiaUser(String firstName, String lastName) {
        fName = firstName;
        lName = lastName;
    }
}
