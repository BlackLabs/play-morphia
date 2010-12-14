package play.modules.morphia;

import java.lang.annotation.Annotation;

import org.bson.types.ObjectId;

import play.data.binding.Global;
import play.data.binding.TypeBinder;

@Global
public class ObjectIdBinder implements TypeBinder<ObjectId> {
    @Override
    public Object bind(String name, Annotation[] annotations, String value, @SuppressWarnings("rawtypes") Class actualClass) throws Exception {
        return new ObjectId(value);
    }
    
}
