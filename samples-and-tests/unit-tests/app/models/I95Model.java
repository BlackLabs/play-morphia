package models;

import com.google.code.morphia.annotations.Entity;
import play.modules.morphia.Model;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 24/10/12
 * Time: 11:15 AM
 * To change this template use File | Settings | File Templates.
 */
@Entity()
public class I95Model extends Model {
    public I95Embedded e;

    public I95Model(String a, String b, String c) {
        e = new I95Embedded(a, b, c);
    }
}
