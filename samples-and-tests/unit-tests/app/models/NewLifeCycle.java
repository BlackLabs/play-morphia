package models;

import com.google.code.morphia.annotations.Entity;

import play.Logger;
import play.modules.morphia.Model;

@SuppressWarnings("serial")
@Entity
public class NewLifeCycle extends Model {
    public String foo;
    public String bar;
    
    public NewLifeCycle(String f, String b) {
        foo = f;
        bar = b;
    }
    
    @OnLoad
    void onLoad() throws Exception {
        Logger.trace("onload");
    }
    
    @Loaded
    void loaded() throws Exception {
        Logger.trace("loaded");
    }
    
    @OnAdd
    protected void persist() throws Exception {
        Logger.trace("onpersist");
    }
    
    @Added
    protected void persisted() {
        Logger.trace("persisted");
    }

    @OnLoad
    @Loaded
    @OnAdd
    @Added
    protected void common() {
        Logger.trace("common");
    }

}
