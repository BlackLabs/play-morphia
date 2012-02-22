package play.modules.morphia.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import play.db.Model;

import java.lang.reflect.Type;

/**
 * Created by IntelliJ IDEA.
 * User: luog
 * Date: 14/01/12
 * Time: 7:52 PM
 * To change this template use File | Settings | File Templates.
 */
public class ModelFactoryGsonAdapter implements JsonSerializer<Model.Factory> {
    @Override
    public JsonElement serialize(Model.Factory src, Type typeOfSrc, JsonSerializationContext context) {
        return null;
    }
}
