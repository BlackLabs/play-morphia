package play.modules.morphia;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.bson.types.CodeWScope;

import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.data.binding.BeanWrapper;
import play.data.validation.Validation;
import play.exceptions.UnexpectedException;
import play.modules.morphia.MorphiaPlugin.MorphiaModelLoader;
import play.modules.morphia.utils.IdGenerator;
import play.modules.morphia.utils.StringUtil;
import play.mvc.Scope.Params;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.Key;
import com.google.code.morphia.annotations.Embedded;
import com.google.code.morphia.annotations.Reference;
import com.google.code.morphia.annotations.Transient;
import com.google.code.morphia.mapping.Mapper;
import com.google.code.morphia.query.Criteria;
import com.google.code.morphia.query.CriteriaContainer;
import com.google.code.morphia.query.CriteriaContainerImpl;
import com.google.code.morphia.query.FieldEnd;
import com.google.code.morphia.query.Query;
import com.google.code.morphia.query.QueryImpl;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MapReduceCommand;
import com.mongodb.MapReduceOutput;

/**
 * This class provides the abstract declarations for all Models. Implementations
 * of these declarations are provided by the MorphiaEnhancer.
 * 
 * @author greenlaw110@gmail.com
 */
public class Model implements Serializable, play.db.Model {
    
    public static final String ALL = "__all__";

    private static final long serialVersionUID = -719759872826848048L;

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
        if (isNew()) return;
        _h_OnDelete();
        ds().delete(this);
        _h_Deleted();
    }

    // -- porting from play.db.GenericModel
    @SuppressWarnings("unchecked")
    public static <T extends Model> T create(Class<?> type, String name,
            Map<String, String[]> params, Annotation[] annotations) {
        try {
            Constructor<?> c = type.getDeclaredConstructor();
            c.setAccessible(true);
            Object model = c.newInstance();
            return (T) edit(model, name, params, annotations);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends Model> T edit(Object o, String name,
            Map<String, String[]> params, Annotation[] annotations) {
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
                  relation = multiple ? ((Class<?>) ((ParameterizedType) field
                        .getGenericType()).getActualTypeArguments()[0]).getName()
                        : clz.getName();
               }

               if (isEntity) {
                  Logger.debug("loading relation: %1$s", relation);
                  Class<Model> c = (Class<Model>) Play.classloader
                        .loadClass(relation);
                  if (Model.class.isAssignableFrom(c)) {
                     MorphiaPlugin.MorphiaModelLoader f = (MorphiaModelLoader) MorphiaPlugin.MorphiaModelLoader
                           .getFactory(c);
                     String keyName = null;
                     if (!isEmbedded) {
                        keyName = f.keyName();
                     }
                     if (multiple
                           && Collection.class.isAssignableFrom(field.getType())) {
                        Collection<Model> l = new ArrayList<Model>();
                        if (SortedSet.class.isAssignableFrom(field.getType())) {
                           l = new TreeSet<Model>();
                        } else if (Set.class.isAssignableFrom(field.getType())) {
                           l = new HashSet<Model>();
                        }
                        Logger.debug("Collection intialized: %1$s", l.getClass()
                              .getName());
                        /*
                         * Embedded class does not support Id
                         */
                        if (!isEmbedded) {
                           String[] ids = params.get(name + "." + field.getName()
                                 + "." + keyName);
                           if (ids != null) {
                              params.remove(name + "." + field.getName() + "."
                                    + keyName);
                              for (String _id : ids) {
                                 if (_id.equals("")) {
                                    continue;
                                 }
                                 try {
                                    l.add(f.findById(_id));
                                 } catch (Exception e) {
                                    Validation.addError(
                                          name + "." + field.getName(),
                                          "validation.notFound", _id);
                                 }
                              }
                           }
                        } else {
                           Logger.debug("multiple embedded objects not supported yet");
                        }
                        bw.set(field.getName(), o, l);
                        Logger.debug(
                              "Entity[%1$s]'s field[%2$s] has been set to %3$s", o
                                    .getClass().getName(), field.getName(), l);
                     } else {
                        String name0 = name + "." + field.getName();
                        String name1 = name0 + "." + keyName;
                        String[] ids = params.get(name1);
                        if (ids != null && ids.length > 0 && !ids[0].equals("")) {
                           params.remove(name1);
                           try {
                              Object to = f.findById(ids[0]);
                              bw.set(field.getName(), o, to);
                           } catch (Exception e) {
                              Validation.addError(name0, "validation.notFound",
                                    ids[0]);
                           }
                        } else if (ids != null && ids.length > 0
                              && ids[0].equals("")) {
                           bw.set(field.getName(), o, null);
                           params.remove(name1);
                        } else {
                           // Fix bug: StackOverflowException when one field reference to null with same type
//                           Object o0 = Model.create(field.getType(), name0,
//                                 params, null);
//                           bw.set(field.getName(), o, o0);
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

    @SuppressWarnings("unchecked")
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

    public boolean validateAndCreate() {
        if (Validation.current().valid(this).ok) {
            return create();
        }
        return false;
    }

    /**
     * This method is deprecated as Embedded object shall not extends Model class
     * and shall not be enhanced
     * 
     * @deprecated
     * @return
     */
    protected boolean isEmbedded_() {
        return false;
    }

    /**
     * MorphiaEnhancer will override this method for sub class with \@Id
     * annotation specified
     * 
     * @return
     */
    protected boolean isUserDefinedId_() {
        return false;
    }

    /**
     * Any sub class with \@Id annotation specified need to rewrite this method
     * 
     * @return
     */
    protected static Object processId_(Object id) {
        return IdGenerator.processId(id);
    }

    /**
     * MorphiaEnhancer will override this method for sub class without \@Embedded
     * annotation specified
     * 
     * If user defined customized \@Id field, it's better to override this
     * method for the sake of performance. Otherwise framework will use
     * reflection to get the value
     * 
     * @return
     */
    public Object getId() {
        return null;
    }
    
    @SuppressWarnings("unchecked")
    public <T> T getId(Class<T> clazz) {
        return (T) getId();
    }

    public final void setId(Object id) {
        if (null != getId()) {
            throw new IllegalStateException(
                    "Cannot set ID to entity with ID presented");
        }
        setId_(id);
    }

    /**
     * MorphiaEnhancer will override this method for sub class without user
     * annotated \@Id fields
     */
    protected void setId_(Object id) {
        throw new UnsupportedOperationException(
                "Please override this method for user marked Id field entity: "
                        + this.getClass().getName());
    }

    private void generateId_() {
        if (isEmbedded_())
            return;
        if (null == getId()) {
            if (isUserDefinedId_()) {
                throw new IllegalStateException(
                        "User defined ID should be populated before persist");
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

    // -- helper utilities
    @Transient
    private transient boolean saved_ = false;

    /**
     * A utility method determine whether this entity is a newly constructed
     * object in memory or represents a data from mongodb
     * 
     * @return true if this is a memory object which has not been saved to db
     *         yet, false otherwise
     */
    public final boolean isNew() {
        return !saved_;
    }

    private void setSaved_() {
        saved_ = true;
    }

    // -- Play JPA style methods
    /**
     * This method has no effect at all
     */
    @SuppressWarnings("unchecked")
    public <T extends Model> T merge() {
        return (T) this;
    }

    /**
     * Refresh the entity state.
     */
    @SuppressWarnings("unchecked")
    public <T extends Model> T refresh() {
        return (T) ds().get(this);
    }

    public static <T extends Model> MorphiaQuery all() {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static Model create(String name, Params params) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    /**
     * Shortcut to createQuery
     * 
     * @return
     */
    public static <T extends Model> MorphiaQuery q() {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static <T extends Model> MorphiaQuery createQuery() {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static <T extends Model> MorphiaQuery disableValidation() {
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

    /**
     * Return a Set of distinct values for the given key
     * 
     * @param key
     * @return a distinct set of key values
     */
    public static Set<?> _distinct(String key) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static Long _max(String field) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static AggregationResult groupMax(String field, String... groupKeys) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static Long _min(String field) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static AggregationResult groupMin(String field, String... groupKeys) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static Long _average(String field) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static AggregationResult groupAverage(String field,
            String... groupKeys) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static Long _sum(String field) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static AggregationResult groupSum(String field, String... groupKeys) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static AggregationResult groupCount(String field,
            String... groupKeys) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }
    
    public static Map<String, Long> _cloud(String field) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    @SuppressWarnings("unchecked")
    public <T extends Model> T delete() {
        if (isNew()) {
            Logger.warn("invocation of delete on new entity ignored");
            return (T) this;
        }
        
        _delete();
        
        return (T) this;
    }
    
    private void _h_OnDelete() {
        postEvent_(MorphiaEvent.ON_DELETE, this);
        MorphiaPlugin.onLifeCycleEvent(MorphiaEvent.ON_DELETE, this);
        h_OnDelete();
        deleteBlobs();
    }
    
    protected void h_OnDelete() {
        // for enhancer usage
    }
    
    private void _h_Deleted() {
        h_Deleted();
        MorphiaPlugin.onLifeCycleEvent(MorphiaEvent.DELETED, this);
        postEvent_(MorphiaEvent.DELETED, this);
    }
    
    protected void h_Deleted() {
        // for enhancer usage
    }
    
    protected void h_OnBatchDelete(MorphiaQuery q) {
        // for enhancer usage
    }
    
    protected void h_BatchDeleted(MorphiaQuery q) {
        // for enhancer usage
    }
    
    protected void deleteBlobs() {
        // for enhancer usage
    }
    
    protected void deleteBlobsInBatch(MorphiaQuery q) {
        // for enhancer usage
    }

    /**
     * store (ie insert) the entity.
     */
    public boolean create() {
        if (isNew()) {
            _save();
            return true;
        }
        return false;
    }

    public static long delete(MorphiaQuery query) {
        return query.delete();
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

    /**
     * Shortcut to createQuery()
     * 
     * @return
     */
    public static <T extends Model> MorphiaQuery find() {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    /**
     * JPA style find method
     * 
     * @param keys
     *            could be either "byKey1[AndKey2[AndKey3...]]" or
     *            "Key1[AndKey2[AndKey3...]]" or "key1 key2..."
     * @param params
     *            number should either be one or the same number of keys
     * @return
     */
    public static <T extends Model> MorphiaQuery find(String keys,
            Object... params) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static <T> List<T> findAll() {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    public static <T extends Model> T findById(Object id) {
        throw new UnsupportedOperationException(
                "Embedded entity does not support this method");
    }

    /**
     * Shortcut to find(String, Object...)
     * 
     * @param keys
     * @param objects
     * @return
     */
    public static <T extends Model> MorphiaQuery q(String keys, Object value) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    /**
     * Morphia style filter method.
     * 
     * <p>
     * if you have MyModel.find("byNameAndAge", "John", 20), you can also use
     * MyModel.filter("name", "John").filter("age", 20) for the same query
     * 
     * @param property
     *            should be the filter name
     * @param value
     *            the filter value
     * @return
     */
    public static <T extends Model> MorphiaQuery filter(String property,
            Object value) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    // -- additional quick access method
    /**
     * Return the first element in the data storage. Return null if there is no
     * record found
     */
    public static <T extends Model> T get() {
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
     * Return MongoDB DBCollection for this model
     */
    public static DBCollection col() {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }

    /**
     * Return MongoDB DB instance
     * 
     * @return
     */
    public static DB db() {
        return ds().getDB();
    }

    /**
     * Save and return this entity
     * 
     * @param <T>
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T extends Model> T save() {
        save2();
        return (T) this;
    }

    /**
     * Save and return Morphia Key
     * 
     * @return
     */
    public Key<? extends Model> save2() {
        boolean isNew = isNew();
        postEvent_(isNew ? MorphiaEvent.ON_ADD : MorphiaEvent.ON_UPDATE, this);
        if (isNew) _h_OnAdd(); else _h_OnUpdate();
        Key<? extends Model> k = ds().save(this);
        saveBlobs();
        if (isNew) {setSaved_();_h_Added();} else _h_Updated();
        return k;
    }
    
    /**
     * for PlayMorphia internal usage only
     */
    public final void _h_OnLoad() {
        postEvent_(MorphiaEvent.ON_LOAD, this);
        MorphiaPlugin.onLifeCycleEvent(MorphiaEvent.ON_LOAD, this);
        h_OnLoad();
    }
    
    /**
     * for PlayMorphia internal usage only
     */
    protected void h_OnLoad() {
        // for enhancer usage
    }
    
    /**
     * for PlayMorphia internal usage only
     */
    public final void _h_Loaded() {
        setSaved_();
        loadBlobs();
        h_Loaded();
        MorphiaPlugin.onLifeCycleEvent(MorphiaEvent.LOADED, this);
        postEvent_(MorphiaEvent.LOADED, this);
    }
    
    /**
     * for PlayMorphia internal usage only
     */
    protected void h_Loaded() {
        // for enhancer usage
    }
    
    /**
     * for PlayMorphia internal usage only
     */
    private void _h_Added() {
        h_Added();
        MorphiaPlugin.onLifeCycleEvent(MorphiaEvent.ADDED, this);
        postEvent_(MorphiaEvent.ADDED, this);
    }
    
    /**
     * for PlayMorphia internal usage only
     */
    protected void h_Added() {
        // used by enhancer
    }
    
    /**
     * for PlayMorphia internal usage only
     */
    private void _h_Updated() {
        h_Updated();
        MorphiaPlugin.onLifeCycleEvent(MorphiaEvent.UPDATED, this);
        postEvent_(MorphiaEvent.UPDATED, this);
    }

    /**
     * for PlayMorphia internal usage only
     */
    protected void h_Updated() {
        // used by enhancer
    }
    
    /**
     * for PlayMorphia internal usage only
     */
    private void _h_OnAdd() {
        postEvent_(MorphiaEvent.ON_ADD, this);
        MorphiaPlugin.onLifeCycleEvent(MorphiaEvent.ON_ADD, this);
        h_OnAdd();
        generateId_();
    }
    
    /**
     * for PlayMorphia internal usage only
     */
    protected void h_OnAdd() {
        // used by enhancer
    }
    
    /**
     * for PlayMorphia internal usage only
     */
    private void _h_OnUpdate() {
        postEvent_(MorphiaEvent.ON_UPDATE, this);
        MorphiaPlugin.onLifeCycleEvent(MorphiaEvent.ON_UPDATE, this);
        h_OnUpdate();
    }
    
    /**
     * for PlayMorphia internal usage only
     */
    protected void h_OnUpdate() {
        // used by enhancer
    }
    
    protected void saveBlobs() {
        // used by enhancer 
    }
    
    protected void loadBlobs() {
        // used by enhander
    }
    
    protected final Map<String, Boolean> blobFieldsTracker = new HashMap<String, Boolean>();
    protected final boolean blobChanged(String fieldName) {
        return (blobFieldsTracker.containsKey(fieldName) && blobFieldsTracker.get(fieldName));
    }
    protected final void setBlobChanged(String fieldName) {
        blobFieldsTracker.put(fieldName, true);
    }
    
    public String getBlobFileName(String fieldName) {
        return getBlobFileName(getClass().getSimpleName(), getId(), fieldName);
    }
    
    public static String getBlobFileName(String className, Object id, String fieldName) {
        return String.format("%s_%s_%s", className, StringUtils.capitalize(fieldName), id);
    }
    
    public static void removeGridFSFiles(String className, Object id, String...fieldNames) {
        for (String fieldName: fieldNames) {
            String fileName = getBlobFileName(className, id, fieldName);
            Blob.delete(fileName);
        }
    }

    public static void removeGridFSFiles(MorphiaQuery q, String... fieldNames) {
        q.retrievedFields(true, "_id");
        for (Key<Model> key: q.fetchKeys()) {
            Object id = key.getId();
            removeGridFSFiles(q.getEntityClass().getSimpleName(), id, fieldNames);
        }
    }

    // -- auto timestamp methods
    public long _getCreated() {
        throw new UnsupportedOperationException(
                "Please annotate model with @AutoTimestamp annotation");
    }

    public long _getModified() {
        throw new UnsupportedOperationException(
                "Please annotate model with @AutoTimestamp annotation");
    }
    
    private static void postEvent_(MorphiaEvent event, Object context) {
        if (MorphiaPlugin.postPluginEvent) PlayPlugin.postEvent(event.getId(), context);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static class MorphiaQuery {
        public static Datastore ds() {
            return MorphiaPlugin.ds();
        }

        private QueryImpl<? extends Model> q_;
        private Class<? extends Model> c_;

        public Query<? extends Model> getMorphiaQuery() {
            return q_;
        }
        
        public DBObject getQueryObject() {
            return q_.getQueryObject();
        }
        
        public DBCollection col() {
            return ds().getCollection(c_);
        }

        // constructor for clone() usage
        private MorphiaQuery() {
        }

        public MorphiaQuery(Class<? extends Model> clazz) {
            // super(clazz, ds().getCollection(clazz), ds());
            q_ = (QueryImpl<? extends Model>) ds().createQuery(clazz);
            c_ = clazz;
        }

        public MorphiaQuery(Class<? extends Model> clazz, DBCollection coll,
                Datastore ds) {
            // super(clazz, coll, ds);
            q_ = new QueryImpl(clazz, coll, ds);
            c_ = clazz;
        }

        public MorphiaQuery(Class<? extends Model> clazz, DBCollection coll,
                Datastore ds, int offset, int limit) {
            // super(clazz, coll, ds, offset, limit);
            q_ = new QueryImpl(clazz, coll, ds, offset, limit);
            c_ = clazz;
        }

        public long delete() {
            long l = count();
            postEvent_(MorphiaEvent.ON_BATCH_DELETE, this);
            MorphiaPlugin.onBatchLifeCycleEvent(MorphiaEvent.ON_BATCH_DELETE, this);
            Model m = null;
            try {
                Constructor c = c_.getDeclaredConstructor();
                if (!c.isAccessible()) {
                    c.setAccessible(true);
                }
                m = (Model)c.newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Cannot init model class", e);
            }
            if (null != m) {
                m.h_OnBatchDelete(this);
                m.deleteBlobsInBatch(this);
            }
            ds().delete(q_);
            if (null != m) {
                m.h_BatchDeleted(this);
            }
            postEvent_(MorphiaEvent.BATCH_DELETED, this);
            return l;
        }

        /**
         * Alias of countAll()
         * 
         * @return
         */
        public long count() {
            return q_.countAll();
        }

        /**
         * Used to simulate JPA.find("byXXAndYY", ...);
         * 
         * @param query
         *            could be either "Key1[AndKey2[AndKey3]]" or
         *            "byKey1[AndKey2[AndKey3]]" or "key1 key2 ..."
         * 
         * @param params
         *            the number of params should either be exactly one or the
         *            number match the key number
         * @return
         */
        public MorphiaQuery findBy(String query, Object... params) {
            if (null == query || params.length == 0) {
                throw new IllegalArgumentException("Invalid query or params");
            }
            if (query.startsWith("by"))
                query = query.substring(2);
            String[] keys = query.split("(And|[,;\\s]+)");

            if ((params.length != 1) && (keys.length != params.length)) {
                throw new IllegalArgumentException(
                        "Query key number does not match the params number");
            }

            Object oneVal = params.length == 1 ? params[0] : null;

            for (int i = 0; i < keys.length; ++i) {
                StringBuilder sb = new StringBuilder(keys[i]);
                sb.setCharAt(0, Character.toLowerCase(sb.charAt(0)));
                q_.filter(sb.toString(), oneVal == null ? params[i] : oneVal);
            }

            return this;
        }

        @Override
        public String toString() {
            return q_.toString();
        }

        // ---------------------------------------------------------------------------
        // JPAQuery style interfaces
        // ---------------------------------------------------------------------------
        public <T> T first() {
            return (T) get();
        }

        /**
         * Set the position to start
         * 
         * @param position
         *            Position of the first element
         * @return A new query
         */
        public <T> MorphiaQuery from(int position) {
            q_.offset(position);
            return this;
        }

        /**
         * Retrieve all results of the query
         * 
         * This is a correspondence to JPAQuery's fetch(), which however, used
         * as another method signature of Morphia Query
         * 
         * @return A list of entities
         */
        public <T extends Model> List<T> fetchAll() {
            return (List<T>) q_.asList();
        }

        /**
         * Retrieve results of the query
         * 
         * @param max
         *            Max results to fetch
         * @return A list of entities
         */
        public <T extends Model> List<T> fetch(int max) {
            return (List<T>) q_.limit(max).asList();
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
        public <T extends Model> List<T> fetch(int page, int length) {
            if (page < 1) {
                page = 1;
            }
            return (List<T>) q_.offset((page - 1) * length).limit(length)
                    .asList();
        }

        // ---------------------------------------------------------------------------
        // Morphia Query, QueryResults, Criteria, CriteriaContainer interface
        // ---------------------------------------------------------------------------

        // for the sake of enhancement
        public Model _get() {
            return q_.get();
        }

        public <T extends Model> T get() {
            return (T) q_.get();
        }

        public <T extends Model> MorphiaQuery filter(String condition,
                Object value) {
            q_.filter(condition, value);
            return this;
        }

        public <T extends Model> Key<T> getKey() {
            return (Key<T>) q_.getKey();
        }

        public <T extends Model> Iterator<T> iterator() {
            return (Iterator<T>) q_.iterator();
        }

        public <T extends Model> List<T> asList() {
            return (List<T>) q_.asList();
        }

        public <T extends Model> List<Key<T>> asKeyList() {
            return ((Query<T>) q_).asKeyList();
        }

        public <T extends Model> Iterable<T> fetch() {
            return (Iterable<T>) q_.fetch();
        }

        public Set<?> distinct(String key) {
            return new HashSet(col().distinct(key, getQueryObject()));
        }
        
        public Map<String, Long> cloud(String field) {
            String map = String.format("function() {if (!this.%s) return; for (index in this.%s) emit(this.tags[index], 1);}", field, field);
            String reduce = "function(previous, current) {var count = 0; for (index in current) count += current[index]; return count;}";
            MapReduceCommand cmd = new MapReduceCommand(col(), map, reduce, null, MapReduceCommand.OutputType.INLINE, q_.getQueryObject());
            MapReduceOutput out = col().mapReduce(cmd);
            Map<String, Long> m = new HashMap<String, Long>();
            for (Iterator<DBObject> itr = out.results().iterator(); itr.hasNext();) {
                DBObject dbo = itr.next();
                m.put((String)dbo.get("_id"), ((Double)dbo.get("value")).longValue());
            }
            return m;
        }
        
        /**
         * 
         * @param groupKeys
         *            could be either "f1Andf2.." or "f1 f2" or "f1,f2"
         * @return
         */
        public List<CommandResult> group(String groupKeys, DBObject initial,
                String reduce, String finalize) {
            DBObject key = new BasicDBObject();
            if (!StringUtil.isEmpty(groupKeys)) {
                if (groupKeys.startsWith("by"))
                    groupKeys = groupKeys.substring(2);
                String[] sa = groupKeys.split("(And|[\\s,;]+)");
                for (String s : sa) {
                    key.put(s, true);
                }
            }
            return (List<CommandResult>) ds().getCollection(c_).group(key,
                    q_.getQueryObject(), initial, reduce, finalize);
        }

        private AggregationResult aggregate_(String field, DBObject initial,
                Long initVal, String reduce, String finalize,
                String... groupKeys) {
            if (null == initial)
                initial = new BasicDBObject();
            initial.put(field, initVal);
            return new AggregationResult(group(StringUtil.join(",", groupKeys),
                    initial, reduce, finalize), field);
        }

        public AggregationResult groupMax(String field, String... groupKeys) {
            String reduce = String
                    .format("function(obj, prev){if (obj.%s > prev.%s) prev.%s = obj.%s}",
                            field, field, field, field);
            return aggregate_(field, null, Long.MIN_VALUE + 1, reduce, null,
                    groupKeys);
        }

        public Long max(String maxField) {
            return groupMax(maxField).getResult();
        }

        public AggregationResult groupMin(String field, String... groupKeys) {
            String reduce = String
                    .format("function(obj, prev){if (obj.%s < prev.%s) prev.%s = obj.%s}",
                            field, field, field, field);
            return aggregate_(field, null, Long.MAX_VALUE - 1, reduce, null,
                    groupKeys);
        }

        public Long min(String minField) {
            return groupMin(minField).getResult();
        }

        public AggregationResult groupAverage(String field, String... groupKeys) {
            DBObject initial = new BasicDBObject();
            initial.put("__count", 0);
            initial.put("__sum", 0);
            String reduce = String.format(
                    "function(obj, prev){prev.__count++; prev.__sum+=obj.%s;}",
                    field);
            String finalize = String.format(
                    "function(prev) {prev.%s = prev.__sum / prev.__count;}",
                    field);
            return aggregate_(field, initial, 0L, reduce, finalize, groupKeys);
        }

        public Long average(String field) {
            return groupAverage(field).getResult();
        }

        public AggregationResult groupSum(String field, String... groupKeys) {
            String reduce = String.format(
                    "function(obj, prev){prev.%s+=obj.%s;}", field, field);
            return aggregate_(field, null, 0L, reduce, null, groupKeys);
        }

        public Long sum(String field) {
            return groupSum(field).getResult();
        }

        public AggregationResult groupCount(String field, String... groupKeys) {
            String reduce = String.format("function(obj, prev){prev.%s++;}", field);
            return aggregate_(field, null, 0L, reduce, null, groupKeys);
        }

        public <T extends Model> Iterable<T> fetchEmptyEntities() {
            return (Iterable<T>) q_.fetchEmptyEntities();
        }

        public <T extends Model> FieldEnd<? extends Query<T>> field(String field) {
            return (FieldEnd<? extends Query<T>>) q_.field(field);
        }

        public <T extends Model> Iterable<Key<T>> fetchKeys() {
            return ((Query<T>) q_).fetchKeys();
        }

        public <T extends Model> FieldEnd<? extends CriteriaContainerImpl> criteria(
                String field) {
            return q_.criteria(field);
        }

        public <T extends Model> CriteriaContainer and(Criteria... criteria) {
            return q_.and(criteria);
        }

        public long countAll() {
            return q_.countAll();
        }

        public <T extends Model> CriteriaContainer or(Criteria... criteria) {
            return q_.or(criteria);
        }

        public <T extends Model> MorphiaQuery where(String js) {
            q_.where(js);
            return this;
        }

        public <T extends Model> MorphiaQuery where(CodeWScope js) {
            q_.where(js);
            return this;
        }

        public <T extends Model> MorphiaQuery order(String condition) {
            q_.order(condition);
            return this;
        }

        public <T extends Model> MorphiaQuery limit(int value) {
            q_.limit(value);
            return this;
        }

        public <T extends Model> MorphiaQuery batchSize(int value) {
            q_.batchSize(value);
            return this;
        }

        public <T extends Model> MorphiaQuery offset(int value) {
            q_.offset(value);
            return this;
        }

        @Deprecated
        public <T extends Model> MorphiaQuery skip(int value) {
            q_.skip(value);
            return this;
        }

        public <T extends Model> MorphiaQuery enableValidation() {
            q_.enableValidation();
            return this;
        }

        public <T extends Model> MorphiaQuery disableValidation() {
            q_.disableValidation();
            return this;
        }

        public <T extends Model> MorphiaQuery hintIndex(String idxName) {
            q_.hintIndex(idxName);
            return this;
        }

        public <T extends Model> MorphiaQuery retrievedFields(boolean include,
                String... fields) {
            q_.retrievedFields(include, fields);
            return this;
        }

        public <T extends Model> MorphiaQuery enableSnapshotMode() {
            q_.enableSnapshotMode();
            return this;
        }

        public <T extends Model> MorphiaQuery disableSnapshotMode() {
            q_.disableSnapshotMode();
            return this;
        }

        public <T extends Model> MorphiaQuery queryNonPrimary() {
            q_.queryNonPrimary();
            return this;
        }

        public <T extends Model> MorphiaQuery queryPrimaryOnly() {
            q_.queryPrimaryOnly();
            return this;
        }

        @Deprecated
        public <T extends Model> MorphiaQuery disableTimeout() {
            return disableCursorTimeout();
        }

        public <T extends Model> MorphiaQuery disableCursorTimeout() {
            q_.disableCursorTimeout();
            return this;
        }

        @Deprecated
        public <T extends Model> MorphiaQuery enableTimeout() {
            return enableCursorTimeout();
        }

        public <T extends Model> MorphiaQuery enableCursorTimeout() {
            q_.enableCursorTimeout();
            return this;
        }

        public Class<? extends Model> getEntityClass() {
            return q_.getEntityClass();
        }

        @Override
        public MorphiaQuery clone() {
            MorphiaQuery mq = new MorphiaQuery();
            mq.q_ = q_.clone();
            return mq;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface ByPass {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE })
    public @interface AutoTimestamp {
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface Column {
        /** The name of the key to store the field in; Defaults to the field name. */
        String value() default Mapper.IGNORED_FIELDNAME;

        /** Specify the concrete class to instantiate. */
        Class<?> concreteClass() default Object.class;
    }

    /**
     * NoID is used to annotate on sub types which is sure to get ID field from
     * parent type
     * 
     * @see https://groups.google.com/d/topic/play-framework/hPWJCvefPoI/discussion
     *      
     * @author luog
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE })
    public @interface NoId {
    }

    /**
     * OnLoad mark a method be called after an new instance of an entity is initialized and
     * before the properties are filled with mongo db columns
     * 
     * @author luog
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    public @interface OnLoad {
    }

    /**
     * OnLoad mark a method be called immediately after an entity loaded from mongodb
     * 
     * @author luog
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    public @interface Loaded {
    }

    /**
     * OnAdd mark a method be called before an new entity is saved. If any exception get thrown
     * out in the method the entity will not be saved
     * 
     * @author luog
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    public @interface OnAdd {
    }

    /**
     * OnUpdate mark a method be called before an existing entity is saved. If any exception get thrown
     * out in the method the entity will not be saved
     * 
     * @author luog
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    public @interface OnUpdate {
    }
    
    /**
     * Added mark a method be called after an new entity is saved.
     * 
     * @author luog
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    public @interface Added {
    }
    
    /**
     * Updated mark a method be called after an existing entity is saved.
     * 
     * @author luog
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    public @interface Updated {
    }
    
    /**
     * OnDelete mark a method be called before an entity is deleted. If any exception throw out
     * in this method the entity will not be removed
     * 
     * @author luog
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    public @interface OnDelete {
    }
    
    /**
     * Deleted mark a method be called after an entity is deleted
     * 
     * @author luog
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    public @interface Deleted {
    }
    
    /**
     * OnBatchDelete mark a method be called before a query's delete method get called. If any exception throw out
     * in this method the query deletion will be canceled
     * 
     * @author luog
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    public @interface OnBatchDelete {
    }
    
    /**
     * Deleted mark a method be called after an a query deletion executed
     * 
     * @author luog
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.METHOD })
    public @interface BatchDeleted {
    }
    
}
