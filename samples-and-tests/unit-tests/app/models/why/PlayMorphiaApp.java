package models.why;

import java.util.List;

public class PlayMorphiaApp {
    
    public static void crud() {
        // create
        PlayMorphiaUser user = new PlayMorphiaUser("John", "Smith")
            .save();
        // read
        PlayMorphiaUser user2 = PlayMorphiaUser.findById(user.getId());
        // update
        user2.fName = "Tom";
        user2.save();
        // delete
        user2.delete();
    }
    
    public static void query() {
        List<PlayMorphiaUser> users = PlayMorphiaUser
                .find("fName, lName", "John", "Smith").asList();
    }
}
