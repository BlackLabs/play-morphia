package models;

import play.modules.morphia.Model;
import com.google.code.morphia.annotations.*;

import java.lang.String;
import java.util.*;

@Entity
public class ExistsQueryTestModel extends Model {
    @Embedded
    public Map<String, MyStuff> bags = new HashMap<String, MyStuff>();

    @Model.Loaded
    void initBags() {
        if (null == bags) bags = new HashMap<String, MyStuff>();
    }
    
    public void addStuff(String key) {
        MyStuff stuff = new MyStuff();
        stuff.key = key;
        bags.put(key, stuff);
    }

    @Embedded
    public static class MyStuff {
        public String key;
    }
}


