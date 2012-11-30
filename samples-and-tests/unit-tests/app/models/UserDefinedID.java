package models;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Id;
import play.modules.morphia.Model;

import java.util.UUID;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 30/11/12
 * Time: 8:44 AM
 * To change this template use File | Settings | File Templates.
 */
@Entity
public class UserDefinedID extends Model {
    @Id
    public String name = UUID.randomUUID().toString();

    @Override
    public <T> T getId() {
        return (T)name;
    }

    @Override
    protected void setId_(Object id) {
        name = String.valueOf(id);
    }

    protected static Object processId_(Object id) {
        return String.valueOf(id);
    }
}
