package play.modules.morphia;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.bson.types.ObjectId;

import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.data.binding.Binder;
import play.db.Model.Factory;
import play.exceptions.UnexpectedException;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.Morphia;
import com.google.code.morphia.annotations.Embedded;
import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Id;
import com.google.code.morphia.annotations.Reference;
import com.google.code.morphia.annotations.Transient;
import com.google.code.morphia.mapping.validation.ConstraintViolationException;
import com.google.code.morphia.query.Criteria;
import com.google.code.morphia.query.Query;
import com.mongodb.DB;
import com.mongodb.Mongo;

/**
 * The plugin for the Morphia module.
 * 
 * @author greenlaw110@gmail.com
 */
public class MorphiaPlugin extends PlayPlugin {
	public static final String VERSION = "1.2beta";
	
    public static final String PREFIX = "morphia.db.";

    private MorphiaEnhancer e_ = new MorphiaEnhancer();

    private static Morphia m_ = null;
    private static Datastore ds_ = null;
    
    private static boolean configured_ = false;
    
    public static boolean configured() {
        return configured_;
    }
    
    public static enum IdType {
        Long,
        ObjectId
    }
    
    private static IdType idType_ = IdType.ObjectId;
    
    public static IdType getIdType() {
        return idType_;
    }

    public static Datastore ds() {
        return ds_;
    }

    @Override
    public void enhance(ApplicationClass applicationClass) throws Exception {
        onConfigurationRead(); //ensure configuration be read before enhancement
        e_.enhanceThisClass(applicationClass);
    }

    @Override
    public void onConfigurationRead() {
        if (configured_) return;
        Logger.trace("Morphia> reading configuration");
        Properties c = Play.configuration;
        Mongo m;
        String host = c.getProperty(PREFIX + "host", "localhost");
        String port = c.getProperty(PREFIX + "port", "27017");
        try {
            m = new Mongo(host, Integer.parseInt(port));
            Logger.trace("MongoDB host: %1$s", host);
            Logger.trace("MongoDB port: %1$s", port);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e.getMessage());
        }
        String dbName = c.getProperty(PREFIX + "name");
        DB db = m.getDB(dbName);
        if (c.containsKey(PREFIX + "username") && c.containsKey(PREFIX + "password")) {
            String username = c.getProperty(PREFIX + "username");
            String password = c.getProperty(PREFIX + "password");
            if (!db.authenticate(username, password.toCharArray())) {
                throw new RuntimeException("MongoDB authentication failed: " + dbName);
            }
        }
        if (c.containsKey("morphia.id.type")) {
            Logger.debug("Morphia> reading id type...");
            String s = c.getProperty("morphia.id.type");
            try {
                idType_ = IdType.valueOf(s);
                Logger.debug("ID Type set to : %1$s", idType_.name());
                if ("1.2beta".equals(VERSION) && idType_ == IdType.Long) {
                	Logger.warn("Caution: Using reference in your model entities might cause problem when you ID type set to Long. Check http://groups.google.com/group/morphia/browse_thread/thread/bdd51121c2845973");
                }
            } catch (Exception e) {
                Logger.warn(e, "Error configure morphia id type: %1$s. Id type set to default: ObjectId.", s);
            }
        }
        m_ = new Morphia();
        ds_ = m_.createDatastore(m, dbName);
        
        configured_ = true;
        
//        // now it's time to enhance the model classes
//        ApplicationClass ac = null;
//        try {
//            for (ApplicationClass ac0: Play.classes.all()) {
//                ac = ac0;
//                new MorphiaEnhancer().enhanceThisClass_(ac);
//            }
//        } catch (Exception e) {
//            throw new UnexpectedException("Error enhancing class: " + ac.name);
//        }
//        afterApplicationStart_();
    }
    
    @Override
    public void onApplicationStart() {
    	configured_ = false;
    	onConfigurationRead();
    }
    
//    @Override
//    public void detectChange() {        
//        ds_.getMongo().close();        
//        onConfigurationRead();
//        afterApplicationStart();
//    }
    
    @Override
    public void afterApplicationStart() {
        List<Class<?>> pending = new ArrayList<Class<?>>();
        Map<Class<?>, Integer> retries = new HashMap<Class<?>, Integer>();
        List<ApplicationClass> cs = Play.classes.all();
        for (ApplicationClass c : cs) {
            Class<?> clz = c.javaClass;
            if (clz.isAnnotationPresent(Entity.class)) {
                try {
                    Logger.debug(">> mapping class: %1$s", clz.getName());
                    m_.map(clz);
                } catch (ConstraintViolationException e) {
                    Logger.error(e, "error mapping class [%1$s]", clz);
                    pending.add(clz);
                    retries.put(clz, 1);
                }
            }
        }

        while (!pending.isEmpty()) {
            for (Class<?> clz : pending) {
                try {
                    Logger.trace(">> mapping class: ", clz.getName());
                    m_.map(clz);
                    pending.remove(clz);
                } catch (ConstraintViolationException e) {
                    Logger.error(e, "error mapping class [%1$s]", clz);
                    int retry = retries.get(clz);
                    if (retry > 2) {
                        throw new RuntimeException("too many errories mapping Morphia Entity classes");
                    }
                    retries.put(clz, retries.get(clz) + 1);
                }
            }
        }
        
        Logger.info("Morphia[%1$s] initialized", VERSION);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object bind(String name, @SuppressWarnings("rawtypes") Class clazz, java.lang.reflect.Type type, Annotation[] annotations, Map<String, String[]> params) {
        if (Model.class.isAssignableFrom(clazz)) {
            String keyName = modelFactory(clazz).keyName();
            String idKey = name + "." + keyName;
            if (params.containsKey(idKey) && params.get(idKey).length > 0 && params.get(idKey)[0] != null && params.get(idKey)[0].trim().length() > 0) {
                String id = params.get(idKey)[0];
                try {
                    Object o = ds().createQuery(clazz).filter(keyName, new ObjectId(id)).get();
                    return Model.edit(o, name, params, annotations);
                } catch (Exception e) {
                    return null;
                }
            }
            return Model.create(clazz, name, params, annotations);
        }
        return super.bind(name, clazz, type, annotations, params);
    }

    @Override
    public Object bind(String name, Object o, Map<String, String[]> params) {
        if (o instanceof Model) {
            return Model.edit(o, name, params, null);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Model.Factory modelFactory(Class<? extends play.db.Model> modelClass) {
        if (Model.class.isAssignableFrom(modelClass) && modelClass.isAnnotationPresent(Entity.class)) {
            return MorphiaModelLoader.getFactory((Class<? extends Model>) modelClass);
        }
        return null;
    }

    public static class MorphiaModelLoader implements Model.Factory {
        
        private static Map<Class<? extends Model>, Model.Factory> m_ = new HashMap<Class<? extends Model>, Factory>();

        private Class<? extends Model> clazz;

        private MorphiaModelLoader(Class<? extends Model> clazz) {
            this.clazz = clazz;
            m_.put(clazz, this);
        }
        
        public static Model.Factory getFactory(Class<? extends Model> clazz) {
            synchronized (m_) {
                Model.Factory f = m_.get(clazz);
                if (null == f) f = new MorphiaModelLoader(clazz);
                return f;
            }
        }

        @Override
        public Model findById(Object id) {
            if (id == null)
                return null;
            try {
                return ds().find(clazz, keyName(), Binder.directBind(id.toString(), Model.Manager.factoryFor(clazz).keyType())).get();
            } catch (Exception e) {
                // Key is invalid, thus nothing was found
                return null;
            }
        }
        
        @Override
        public List<play.db.Model> fetch(int offset, int size, String orderBy, String order,
                List<String> searchFields, String keywords, String where) {
            if (orderBy == null)
                orderBy = keyName();
            if ("DESC".equalsIgnoreCase(order))
                orderBy = null == orderBy ? null : "-" + orderBy;
            Query<? extends Model> q = ds().createQuery(clazz).offset(offset).limit(size);
            if (null != orderBy) q = q.order(orderBy);

            if (keywords != null && !keywords.equals("")) {
            	List<Criteria> cl = new ArrayList<Criteria>();
                for (String f : fillSearchFieldsIfEmpty_(searchFields)) {
                	cl.add(q.criteria(f).contains(keywords));
                }
                q.or(cl.toArray(new Criteria[]{}));
            }

            if (null != where && !"".equals(where)) {
                Logger.warn("'where' condition not supported yet, it will be discarded: %1$s", where);
            }
            List<play.db.Model> l = new ArrayList<play.db.Model>();
            l.addAll(q.asList());
            return l;
        }
        
        private List<String> fillSearchFieldsIfEmpty_(List<String> l) {
            if (l == null) {
                l = new ArrayList<String>();
            }
            if (l.isEmpty()) {
                for (Model.Property property : listProperties()) {
                    if (property.isSearchable) l.add(property.name);
                }
            }
            return l;
        }

        @Override
        public Long count(List<String> searchFields, String keywords, String where) {
            Query<?> q = ds().createQuery(clazz);

            if (keywords != null && !keywords.equals("")) {
            	List<Criteria> cl = new ArrayList<Criteria>();
                for (String f : fillSearchFieldsIfEmpty_(searchFields)) {
                	cl.add(q.criteria(f).contains(keywords));
                }
                q.or(cl.toArray(new Criteria[]{}));
            }

            if (null != where || !"".equals(where)) {
                Logger.warn("'where' condition not supported yet, it will be discarded: %1$s", where);
            }
            return q.countAll();
        }

        public void deleteAll() {
            ds().delete(ds().createQuery(clazz));
        }

        public List<Model.Property> listProperties() {
            List<Model.Property> properties = new ArrayList<Model.Property>();
            Set<Field> fields = new HashSet<Field>();
            Class<?> tclazz = clazz;
            while (!tclazz.equals(Object.class)) {
                Collections.addAll(fields, tclazz.getDeclaredFields());
                tclazz = tclazz.getSuperclass();
            }
            for (Field f : fields) {
                if (Modifier.isTransient(f.getModifiers())) {
                    continue;
                }
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                if (f.isAnnotationPresent(Transient.class)) {
                    continue;
                }
                Model.Property mp = buildProperty(f);
                if (mp != null) {
                    properties.add(mp);
                }
            }
            return properties;
        }

        public String keyName() {
            Field f = keyField();
            return (f == null) ? null : f.getName();
        }

        public Class<?> keyType() {
            return keyField().getType();
        }

        @Override
        public Object keyValue(play.db.Model m) {
            Field k = keyField();
            try {
                // Embedded class has no key value
                return null != k ? k.get(m) : null;
            } catch (Exception ex) {
                throw new UnexpectedException(ex);
            }
        }

        //

        Field keyField() {
            Class<?> c = clazz;
            try {
                while (!c.equals(Object.class)) {
                    for (Field field : c.getDeclaredFields()) {
                        if (field.isAnnotationPresent(Id.class)) {
                            field.setAccessible(true);
                            return field;
                        }
                    }
                    c = c.getSuperclass();
                }
            } catch (Exception e) {
                throw new UnexpectedException("Error while determining the object @Id for an object of type "
                        + c);
            }
            return null;
        }

        Model.Property buildProperty(final Field field) {
            Model.Property modelProperty = new Model.Property();
            modelProperty.type = field.getType();
            modelProperty.field = field;
            if (Model.class.isAssignableFrom(field.getType())) {
                if (field.isAnnotationPresent(Embedded.class)) {
                    modelProperty.isRelation = true;
                    modelProperty.relationType = field.getType();
                    modelProperty.choices = new Model.Choices() {
                        @SuppressWarnings("unchecked")
                        public List<Object> list() {
                            // it doesn't make sense to compose choice list for
                            // embedded field
                            return Collections.EMPTY_LIST;
                        }
                    };
                }
                if (field.isAnnotationPresent(Reference.class)) {
                    modelProperty.isRelation = true;
                    modelProperty.relationType = field.getType();
                    modelProperty.choices = new Model.Choices() {
                        @SuppressWarnings({ "unchecked" })
                        public List<Object> list() {
                            return (List<Object>) ds().createQuery(field.getType()).asList();
                        }
                    };
                }
            }
            if (Collection.class.isAssignableFrom(field.getType())) {
                final Class<?> fieldType = (Class<?>) ((ParameterizedType) field.getGenericType())
                        .getActualTypeArguments()[0];
                if (field.isAnnotationPresent(Reference.class)) {
                    modelProperty.isRelation = true;
                    modelProperty.isMultiple = true;
                    modelProperty.relationType = fieldType;
                    modelProperty.choices = new Model.Choices() {
                        @SuppressWarnings("unchecked")
                        public List<Object> list() {
                            return (List<Object>) ds().createQuery(field.getType()).asList();
                        }
                    };
                }
            }
            modelProperty.name = field.getName();
            if (field.getType().equals(String.class)) {
                modelProperty.isSearchable = true;
            }
            return modelProperty;
        }

    }
    
}
