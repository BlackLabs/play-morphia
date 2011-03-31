package morphia;

import java.lang.annotation.Annotation;

import org.bson.types.ObjectId;

import play.data.binding.Global;
import play.data.binding.TypeBinder;

@Global
public class ObjectIdBinder implements TypeBinder<ObjectId> {

    @Override
    public Object bind(String name, Annotation[] annotations, String value,
            Class actualClass) throws Exception {
        return new ObjectId(value);
    }
    
}
