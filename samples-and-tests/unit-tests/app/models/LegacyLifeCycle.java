package models;

import play.Logger;
import play.modules.morphia.Model;

import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.PostLoad;
import org.mongodb.morphia.annotations.PostPersist;
import org.mongodb.morphia.annotations.PreLoad;
import org.mongodb.morphia.annotations.PrePersist;

@SuppressWarnings("serial")
@Entity
public class LegacyLifeCycle extends Model {
    
    public String foo;
    public String bar;
    
    public LegacyLifeCycle(String f, String b) {
        foo = f;
        bar = b;
    }
    
    @PreLoad
    void onLoad() throws Exception {
        Logger.trace("onload");
    }
    
    @PostLoad
    void loaded() throws Exception {
        Logger.trace("loaded");
    }
    
    @PrePersist
    protected void persist() throws Exception {
        Logger.trace("onpersist");
    }
    
    @PostPersist
    protected void persisted() {
        Logger.trace("persisted");
    }

    @PreLoad
    @PostLoad
    @PrePersist
    @PostPersist
    protected void common() {
        Logger.trace("common");
    }
    
}
