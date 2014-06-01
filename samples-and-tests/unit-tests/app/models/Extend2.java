package models;

import org.mongodb.morphia.annotations.Entity;
import play.modules.morphia.Model;

import java.lang.String;

@SuppressWarnings("serial")
@Entity
public class Extend2 extends Base {
    public String name;
}
