package models;

import org.bson.types.ObjectId;

import play.modules.morphia.Model.Added;
import play.modules.morphia.Model.OnAdd;
import play.modules.morphia.Model.OnLoad;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Id;
import com.google.code.morphia.annotations.PostPersist;
import com.google.code.morphia.annotations.PrePersist;
import com.google.code.morphia.annotations.PreSave;

@Entity
public class PureMorphiaModel {
    
    @Id
    public ObjectId id;
    
    public String fName;
    public String lName;
    
    @OnAdd
    void onAdd() {
        Event.newEvent("foo", OnLoad.class);
    }
    
    @Added
    void added() {
        Event.newEvent("foo", Added.class);
    }
    
    @PreSave
    void preSave() {
        Event.newEvent("foo", PreSave.class);
    }
    
    @PrePersist
    void prePersist() {
        Event.newEvent("foo", PrePersist.class);
    }
    
    @PostPersist
    void postSave() {
        Event.newEvent("foo", PostPersist.class);
    }
}
