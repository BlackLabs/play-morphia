package play.modules.morphia.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import play.modules.morphia.Blob;

import java.lang.reflect.Type;

/**
 * Created by IntelliJ IDEA.
 * User: luog
 * Date: 14/01/12
 * Time: 7:50 PM
 * To change this template use File | Settings | File Templates.
 */
public class BlobGsonAdapter implements JsonSerializer<Blob> {
    @Override
    public JsonElement serialize(Blob src, Type typeOfSrc, JsonSerializationContext context) {
        return null;
    }
}
