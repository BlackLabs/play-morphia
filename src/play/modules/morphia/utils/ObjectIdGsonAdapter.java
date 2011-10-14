package play.modules.morphia.utils;

import java.lang.reflect.Type;

import org.bson.types.ObjectId;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public class ObjectIdGsonAdapter implements JsonSerializer<ObjectId>,
        JsonDeserializer<ObjectId> {

    @Override
    public ObjectId deserialize(JsonElement json, Type typeOfT  ,
            JsonDeserializationContext context) throws JsonParseException {
        return new ObjectId(json.getAsJsonPrimitive().getAsString());
    }

    @Override
    public JsonElement serialize(ObjectId src, Type typeOfSrc,
            JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
    }

}
