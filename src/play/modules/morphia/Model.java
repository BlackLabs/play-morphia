package play.modules.morphia;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.bson.types.CodeWScope;
import org.bson.types.ObjectId;

import play.Logger;
import play.Play;
import play.data.binding.BeanWrapper;
import play.data.validation.Validation;
import play.exceptions.UnexpectedException;
import play.modules.morphia.utils.IdGenerator;
import play.mvc.Scope.Params;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.Key;
import com.google.code.morphia.annotations.Embedded;
import com.google.code.morphia.annotations.PrePersist;
import com.google.code.morphia.annotations.Reference;
import com.google.code.morphia.query.Query;
import com.google.code.morphia.query.QueryFieldEnd;
import com.google.code.morphia.query.QueryImpl;
import com.mongodb.DB;
import com.mongodb.DBCollection;

/**
 * This class provides the abstract declarations for all Models. Implementations
 * of these declarations are provided by the MorphiaEnhancer.
 * 
 * @author greenlaw110@gmail.com
 */
public class Model implements Serializable, play.db.Model {

    // -- play.db.Model interface
    @Override
    public Object _key() {
        return getId();
    }

    @Override
    public void _save() {
        save();
    }

    @Override
    public void _delete() {
        delete();
    }

    // -- porting from play.db.GenericModel
    @SuppressWarnings("unchecked")
    public static <T extends Model> T create(Class<?> type, String name, Map<String, String[]> params, Annotation[] annotations) {
        try {
            Constructor<?> c = type.getDeclaredConstructor();
            c.setAccessible(true);
            Object model = c.newInstance();
            return (T)edit(model, name, params, annotations);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Model> T edit(Object o, String name, Map<String, String[]> params,
            Annotation[] annotations) {
        try {
            BeanWrapper bw = new BeanWrapper(o.getClass());
            // Start with relations
            Set<Field> fields = new HashSet<Field>();
            Class<?> clazz = o.getClass();
            while (!clazz.equals(Object.class)) {
                Collections.addAll(fields, clazz.getDeclaredFields());
                clazz = clazz.getSuperclass();
            }
            for (Field field : fields) {
                boolean isEntity = false;
                String relation = null;
                boolean multiple = false;
                boolean isEmbedded = field.isAnnotationPresent(Embedded.class);
                
                if (isEmbedded || field.isAnnotationPresent(Reference.class)) {
                    isEntity = true;
                    multiple = false;
                    Class<?> clz = field.getType();
                    Class<?>[] supers = clz.getInterfaces();
                    for (Class<?> c : supers) {
                        if (c.equals(Collection.class)) {
                            multiple = true;
                            break;
                        }
                    }
                    // TODO handle Map<X, Y> relationship
                    // TODO handle Collection<Collection2<..>>
                    relation = multiple ? ((Class<?>)((ParameterizedType) field.getGenericType())
                            .getActualTypeArguments()[0]).getName() : clz.getName();
                }

                if (isEntity) {
                    Logger.debug("loading relation: %1$s", relation);
                    Class<Model> c = (Class<Model>) Play.classloader.loadClass(relation);
                    if (Model.class.isAssignableFrom(c)) {
                        String keyName = null;
                        if (!isEmbedded) {
                            play.db.Model.Factory f = MorphiaPlugin.MorphiaModelLoader.getFactory((Class<? extends Model>) o.getClass());
                            keyName = f.keyName();
                        }
                        if (multiple && Collection.class.isAssignableFrom(field.getType())) {
                            Collection l = new ArrayList();
                            if (SortedSet.class.isAssignableFrom(field.getType())) {
                                l = new TreeSet();
                            } else if (Set.class.isAssignableFrom(field.getType())) {
                                l = new HashSet();
                            }
                            Logger.debug("Collection intialized: %1$s", l.getClass().getName());
                            /*
                             * Embedded class does not support Id
                             */
                            if (!isEmbedded) {
                                String[] ids = params.get(name + "." + field.getName() + "." + keyName);
                                if (ids != null) {
                                    params.remove(name + "." + field.getName() + "." + keyName);
                                    for (String _id : ids) {
                                        if (_id.equals("")) {
                                            continue;
                                        }
                                        Query q = ds().createQuery(c).filter(keyName, new ObjectId(_id));
                                        try {
                                            l.add(q.get());
                                        } catch (Exception e) {
                                            Validation.addError(name + "." + field.getName(),
                                                    "validation.notFound", _id);
                                        }
                                    }
                                }
                            }
                            bw.set(field.getName(), o, l);
                            Logger.debug("Entity[%1$s]'s field[%2$s] has been set to %3$s", o.getClass().getName(), field.getName(), l);
                        } else {
                            String[] ids = params.get(name + "." + field.getName() + "." + keyName);
                            if (ids != null && ids.length > 0 && !ids[0].equals("")) {
                                params.remove(name + "." + field.getName() + "." + keyName);
                                Query q = ds().createQuery(c).filter(keyName, processId_(ids[0]));
                                try {
                                    Object to = q.get();
                                    bw.set(field.getName(), o, to);
                                } catch (Exception e) {
                                    Validation.addError(name + "." + field.getName(), "validation.notFound",
                                            ids[0]);
                                }
                            } else if (ids != null && ids.length > 0 && ids[0].equals("")) {
                                bw.set(field.getName(), o, null);
                                params.remove(name + "." + field.getName() + "." + keyName);
                            }
                        }
                    }
                }
            }
            bw.bind(name, o.getClass(), params, "", o, annotations);
            return (T) o;
        } catch (Exception e) {
            throw new UnexpectedException(e);
        }
    }

    public <T extends Model> T edit(String name, Map<String, String[]> params) {
        edit(this, name, params, new Annotation[0]);
        return (T) this;
    }

    public boolean validateAndSave() {
        if (Validation.current().valid(this).ok) {
            save();
            return true;
        }
        return false;
    }
    
    /**
     * MorphiaEnhancer will override this method for sub class
     * with \@Embedded annotation specified
     * @return
     */
    protected boolean isEmbedded_() {
        return false;
    }

    /**
     * MorphiaEnhancer will override this method for sub class
     * with \@Id annotation specified
     * @return
     */
    protected boolean isUserDefinedId_() {
        return false;
    }
    
    /**
     * Any sub class with \@Id annotation specified need to
     * rewrite this method
     * @return
     */
    protected static Object processId_(Object id) {
        return IdGenerator.processId(id);
    }

    /**
     * MorphiaEnhancer will override this method for sub class without
     * \@Embedded annotation specified
     * 
     * @return
     */
    public Object getId() {
        return null;
    }
    
    public void setId(Object id) {
        if (null != getId()) {
            throw new IllegalStateException("Cannot set ID to entity with ID presented");
        }
        setId_(id);
    }
    
    /**
     * MorphiaEnhancer will override this method for sub class without user annotated \@Id fields 
     */
    protected void setId_(Object id) {
        throw new UnsupportedOperationException("Please override this method for user marked Id field entity: " + this.getClass().getName());
    }
    
    @PrePersist
    private void generateId_() {
        if (isEmbedded_()) return;
        if (null == getId()) {
            if (isUserDefinedId_()) {
                throw new IllegalStateException("User defined ID should be populated before persist");
            } else {
                setId_(IdGenerator.generateId(this));
            }
        }
    }
    
    public static play.db.Model.Factory getModelFactory() {
        throw new UnsupportedOperationException(
            "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");        
    }

    // -- common object methods
    @Override
    public String toString() {
        String id = getId() == null ? "empty_key" : getId().toString();
        return getClass().getSimpleName() + "[" + id + "]";
    }

    /**
     * For sub class with \@Embedded annotation specified, it's better to
     * override this method
     */
    @Override
    public int hashCode() {
        Object oid = getId();
        return null == oid ? 0 : oid.hashCode();
    }

    /**
     * For sub class with \@Embedded annotation specified, it's better to
     * override this method
     */
    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if ((this == other)) {
            return true;
        }
        if (!this.getClass().isAssignableFrom(other.getClass())) {
            return false;
        }
        Object oid = getId();
        if (oid == null) {
            return false;
        }
        return oid.equals(((Model) other).getId());
    }

    // -- Play JPA style methods
    /**
     * This method has no effect at all
     */
    public <T extends Model> T merge() {
        return (T) this;
    }
    
    /**
     * Refresh the entity state.
     */
    public <T extends Model> T refresh() {
        return (T) ds().get(this);
    }
    
    public static MorphiaQuery all() {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }
    
    public static Model create(String name, Params params) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static MorphiaQuery createQuery() {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static long count() {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static long count(String keys, Object... params) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public void delete() {
        ds().delete(this);
    }

    public static long delete(Query query) {
        long l = query.countAll();
        ds().delete(query);
        return l;
    }

    /**
     * Shortcut to Model.delete(find())
     * 
     * @return
     */
    public static long deleteAll() {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static MorphiaQuery find() {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }
    
    /**
     * JPA style find method 
     * @param keys should be in style of "byKey1[AndKey2[AndKey3...]]"
     * @param params number should eithe be one or the same number of keys
     * @return
     */
    public static MorphiaQuery find(String keys, Object... params) {
        throw new UnsupportedOperationException(
            "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");        
    }
    
    public static <T extends Model> List<T> findAll() {
        throw new UnsupportedOperationException(
        "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");        
    }

    public static <T extends Model> T findById(Object id) {
        throw new UnsupportedOperationException("Embedded entity does not support this method");
    }

    public static <V> MorphiaQuery filter(String property, V value) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    /**
     * Return Morphia Datastore instance
     * 
     * @return
     */
    public static Datastore ds() {
        return MorphiaPlugin.ds();
    }

    /**
     * Return MongoDB DB instance
     * 
     * @return
     */
    public static DB db() {
        return ds().getDB();
    }

    public Key<? extends Model> save() {
        return ds().save(this);
    }

    public static class MorphiaQuery extends QueryImpl implements Query{
        
        public static Datastore ds() {
            return MorphiaPlugin.ds();
        }

        @SuppressWarnings("rawtypes")
        private Query<? extends Model> q_;
        
        public MorphiaQuery(Class clazz) {
            super(clazz, ds().getCollection(clazz), ds());
            q_ = ds().createQuery(clazz);
        }

        public MorphiaQuery(Class clazz, DBCollection coll, Datastore ds) {
            super(clazz, coll, ds);
            q_ = new QueryImpl(clazz, coll, ds);
        }

        public MorphiaQuery(Class clazz, DBCollection coll, Datastore ds, int offset, int limit) {
            super(clazz, coll, ds, offset, limit);
            q_ = new QueryImpl(clazz, coll, ds, offset, limit);
        }

//        public Query<?> getRealQuery() {
//            return q_;
//        }
        public void delete() {
            ds().delete(this);
        }

        @Override
        public String toString() {
            return q_.toString();
        }

        // -- Play style queries
        public long count() {
            return q_.countAll();
        }

        @SuppressWarnings("unchecked")
        public <T extends Model> T first() {
            return (T) get();
        }

        @SuppressWarnings("unchecked")
        public List<? extends Model> fetch(int max) {
            return q_.limit(max).asList();
        }
        
        /**
         * Used to simulate JPA.find("byXXAndYY", ...);
         * @param query should be in style "Key1[AndKey2[AndKey3]]" Note, no "by" prefixed
         * @param params the number of params should either be exactly one or the number match
         *        the key number
         * @return
         */
        public MorphiaQuery findBy(String query, Object ... params) {
            if (null == query || params.length == 0) {
                throw new IllegalArgumentException("Invalid query or params");
            }
            String[] keys = query.split("And");
            
            if ((params.length != 1) && (keys.length != params.length)) {
                throw new IllegalArgumentException("Query key number does not match the params number");
            }
            
            Object oneVal = params.length == 1 ? params[0] : null;
            
            for (int i = 0; i < keys.length; ++i) {
                q_.filter(keys[i].toLowerCase(), oneVal == null ? params[i] : oneVal);
            }
            
            return this;
        }

        /**
         * Retrieve a page of result
         * 
         * @param page
         *            Page number (start at 1)
         * @param length
         *            (page length)
         * @return a list of entities
         */
        @SuppressWarnings("unchecked")
        public List<? extends Model> fetch(int page, int length) {
            if (page < 1) {
                page = 1;
            }
            q_.offset((page - 1) * length);
            q_.limit(length);
            return q_.asList();
        }

        public MorphiaQuery from(int position) {
            return (MorphiaQuery) offset(position);
        }
        
        // -- Morphia Query interface
        @Override
        public List asKeyList() {
            return q_.asKeyList();
        }

        @Override
        public List asList() {
            return q_.asList();
        }

        @Override
        public long countAll() {
            return q_.countAll();
        }

        @Override
        public Query disableValidation() {
            q_.disableValidation();
            return this;
        }

        @Override
        public Query enableValidation() {
            q_.enableValidation();
            return this;
        }

        @Override
        public Iterable fetch() {
            return q_.fetch();
        }

        @Override
        public Model get() {
            return q_.get();
        }

        @Override
        public Class getEntityClass() {
            return q_.getEntityClass();
        }

        @Override
        public Key getKey() {
            return q_.getKey();
        }

        @Override
        public Iterable fetchEmptyEntities() {
            return q_.fetchEmptyEntities();
        }

        @Override
        public Iterable fetchKeys() {
            return q_.fetchKeys();
        }

        @Override
        public QueryFieldEnd field(String fieldExpr) {
            return new QueryFieldEndImpl(fieldExpr, this);
        }

        @Override
        public Query filter(String condition, Object value) {
            q_.filter(condition, value);
            return this;
        }

        public Query hintIndex(String idxName) {
            q_.hintIndex(idxName);
            return this;
        }

        @Override
        public Iterator iterator() {
            return q_.iterator();
        }

        public Query limit(int value) {
            q_.limit(value);
            return this;
        }

        public Query offset(int value) {
            q_.offset(value);
            return this;
        }

        public Query order(String condition) {
            q_.order(condition);
            return this;
        }

        public Query retrievedFields(boolean include, String... fields) {
            q_.retrievedFields(include, fields);
            return this;
        }

        @Override
        public Query where(String js) {
            q_.where(js);
            return this;
        }

        @Override
        public Query where(CodeWScope js) {
            q_.where(js);
            return this;
        }

        @Override
        @Deprecated
        public Query skip(int value) {
            q_.skip(value);
            return this;
        }

    }

}
