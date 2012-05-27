package models;

import com.google.code.morphia.annotations.Entity;
import play.data.validation.Unique;
import play.modules.morphia.Model;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 26/05/12
 * Time: 11:49 PM
 * To change this template use File | Settings | File Templates.
 */
@Entity
public class UniqueChecker extends Model {
    @Unique("foo, bar")
    public String name;

    public String foo;

    public String bar;

    public UniqueChecker(String nm, String f, String b) {
        name = nm;
        foo = f;
        bar = b;
    }

    public void vo1() {}

}
