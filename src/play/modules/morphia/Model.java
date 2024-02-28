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
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.bson.types.CodeWScope;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.DatastoreImpl;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Reference;
import org.mongodb.morphia.annotations.Transient;
import org.mongodb.morphia.mapping.Mapper;
import org.mongodb.morphia.query.Criteria;
import org.mongodb.morphia.query.CriteriaContainer;
import org.mongodb.morphia.query.CriteriaContainerImpl;
import org.mongodb.morphia.query.FieldEnd;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.QueryImpl;
import org.mongodb.morphia.query.UpdateOperations;
import org.mongodb.morphia.query.UpdateOpsImpl;
import org.mongodb.morphia.query.UpdateResults;
import org.osgl.exception.UnsupportedException;
import org.osgl.util.C;
import org.osgl.util.E;
import org.osgl.util.S;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MapReduceCommand;
import com.mongodb.MapReduceOutput;
import com.mongodb.WriteConcern;
import com.mongodb.WriteResult;
import com.mongodb.gridfs.GridFS;

import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.data.binding.BeanWrapper;
import play.data.binding.Binder;
import play.data.binding.ParamNode;
import play.data.validation.Validation;
import play.exceptions.UnexpectedException;
import play.modules.morphia.utils.IdGenerator;
import play.mvc.Scope.Params;

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
        deleted_ = true;
    }

    // -- porting from play.db.GenericModel
    /**
     * This method is deprecated. Use this instead:
     *
     *  public static <T extends Model> T create(ParamNode rootParamNode, String name, Class<?> type, Annotation[] annotations)
     */
    @Deprecated
    public static <T extends Model> T create(Class<?> type, String name, Map<String, String[]> params, Annotation[] annotations) {
        ParamNode rootParamNode = ParamNode.convert(params);
        return (T)create(rootParamNode, name, type, annotations);
    }

    public static <T extends Model> T create(ParamNode rootParamNode, String name, Class<?> type, Annotation[] annotations) {
        try {
            Constructor c = type.getDeclaredConstructor();
            c.setAccessible(true);
            Object model = c.newInstance();
            return (T) edit(rootParamNode, name, model, annotations);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @SuppressWarnings("deprecation")
    public static <T extends Model> T edit(ParamNode rootParamNode, String name, Object o, Annotation[] annotations) {
        ParamNode paramNode = rootParamNode.getChild(name, true);
        // #1195 - Needs to keep track of whick keys we remove so that we can restore it before
        // returning from this method.
        List<ParamNode.RemovedNode> removedNodesList = new ArrayList<ParamNode.RemovedNode>();
        try {
            BeanWrapper bw = new BeanWrapper(o.getClass());
            // Start with relations
            Set<Field> fields = new HashSet<Field>();
            Class clazz = o.getClass();
            while (!clazz.equals(Object.class)) {
                Collections.addAll(fields, clazz.getDeclaredFields());
                clazz = clazz.getSuperclass();
            }
            for (Field field : fields) {
                boolean isEntity = false;
                String relation = null;
                boolean multiple = false;
                boolean isEmbedded = field.isAnnotationPresent(Embedded.class);
                //
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

                    ParamNode fieldParamNode = paramNode.getChild(field.getName(), true);

                    Class<Model> c = (Class<Model>) Play.classloader.loadClass(relation);
                    if (Model.class.isAssignableFrom(c)) {
                        String keyName = Model.Manager.factoryFor(c).keyName();
                        if (multiple && Collection.class.isAssignableFrom(field.getType())) {
                            Collection l = new ArrayList();
                            if (SortedSet.class.isAssignableFrom(field.getType())) {
                                l = new TreeSet();
                            } else if (Set.class.isAssignableFrom(field.getType())) {
                                l = new HashSet();
                            }
                            String[] ids = fieldParamNode.getChild(keyName, true).getValues();
                            if (ids != null) {
                                // Remove it to prevent us from finding it again later
                                fieldParamNode.removeChild(keyName, removedNodesList);
                                for (String _id : ids) {
                                    if (_id.equals("")) {
                                        continue;
                                    }

                                    MorphiaQuery q = new MorphiaQuery(c);
                                    q.filter(keyName, Binder.directBind(rootParamNode.getOriginalKey(), annotations, _id, Model.Manager.factoryFor((Class<Model>) Play.classloader.loadClass(relation)).keyType(), null));
                                    Object to = q.get();
                                    if (to != null) {
                                        l.add(q.get());
                                    }
                                }
                                bw.set(field.getName(), o, l);
                            }
                        } else {
                            String[] ids = fieldParamNode.getChild(keyName, true).getValues();
                            if (ids != null && ids.length > 0 && !ids[0].equals("")) {

                                MorphiaQuery q = new MorphiaQuery(c);
                                q.filter(keyName, Binder.directBind(rootParamNode.getOriginalKey(), annotations, ids[0], Model.Manager.factoryFor((Class<Model>) Play.classloader.loadClass(relation)).keyType(), null));
                                Object to = q.get();
                                if (to != null) {
                                    edit(paramNode, field.getName(), to, field.getAnnotations());
                                    // Remove it to prevent us from finding it again later
                                    paramNode.removeChild( field.getName(), removedNodesList);
                                    bw.set(field.getName(), o, to);
                                }

                            } else if (ids != null && ids.length > 0 && ids[0].equals("")) {
                                bw.set(field.getName(), o, null);
                                // Remove the key to prevent us from finding it again later
                                fieldParamNode.removeChild(keyName, removedNodesList);
                            }
                        }
                    }
                }
            }
            ParamNode beanNode = rootParamNode.getChild(name, true);
            Binder.bindBean(beanNode, o, annotations);
            return (T) o;
        } catch (Exception e) {
            throw new UnexpectedException(e);
        } finally {
            // restoring changes to paramNode
            ParamNode.restoreRemovedChildren( removedNodesList );
        }
    }

    /**
     * This method is deprecated. Use this instead:
     *
     *  public static <T extends Model> T edit(ParamNode rootParamNode, String name, Object o, Annotation[] annotations)
     *
     * @return
     */
    @Deprecated
    public static <T extends Model> T edit(Object o, String name, Map<String, String[]> params, Annotation[] annotations) {
        ParamNode rootParamNode = ParamNode.convert(params);
        return (T)edit( rootParamNode, name, o, annotations);
    }

    /**
     * This method is deprecated. Use this instead:
     *
     *  public <T extends Model> T edit(ParamNode rootParamNode, String name)
     */
    @Deprecated
    public <T extends Model> T edit(String name, Map<String, String[]> params) {
        ParamNode rootParamNode = ParamNode.convert(params);
        return (T)edit(rootParamNode, name, this, null);
    }

    public <T extends Model> T edit(ParamNode rootParamNode, String name) {
        edit(rootParamNode, name, this, null);
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
  @Deprecated
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
    public <T> T getId() {
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> T getId(Class<T> clazz) {
        return (T) getId();
    }

    public String getIdAsStr() {
        return String.valueOf(getId());
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
        toBeEnhanced("Please override this method for user marked Id field entity: %s", getClass().getName());
    }
    
    private void generateId_() {
        if (isEmbedded_())
            return;
        if (null == getId() || S.empty(getIdAsStr())) {
            if (isUserDefinedId_()) {
                throw new IllegalStateException(
                        "User defined ID should be populated before persist");
            } else {
                setId_(IdGenerator.generateId(this));
            }
        }
    }

    public static play.db.Model.Factory getModelFactory() {
        throw toBeEnhanced();
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
    
    @Transient
    private transient boolean deleted_ = false;

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
    
    public final boolean isDeleted() {
        return deleted_;
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

    public static class MorphiaBatchUpdates<T extends Model> {
    private final MorphiaUpdateOperations o;
    private final Model m;
        private <T extends Model> MorphiaBatchUpdates(T model) {
            o = new MorphiaUpdateOperations(model.getClass());
            m = model;
        }
        public MorphiaBatchUpdates _set(String fieldExpr, Object ... values) {
            o.set(fieldExpr, values);
            return this;
        }
        public MorphiaBatchUpdates _unset(String fieldExpr) {
            o.unset(fieldExpr);
            return this;
        }
        public <T extends Model> T commit() {
            o.update(new MorphiaQuery(m.getClass()).filter("_id", m.getId()));
            return (T)m;
        }
    }

    public MorphiaBatchUpdates startBatchUpates() {
        return new MorphiaBatchUpdates(this);
    }
    
    public <T extends Model> T _update(String fieldExpr, Object ... values) {
        return _update(false, fieldExpr, values);
    }

    public <T extends Model> T _update(boolean noRefresh, String fieldExpr, Object ... values) {
        if (null == values) values = new Object[]{null};
        if (values.length == 0) throw new IllegalArgumentException("At least one value required");
        _h_OnUpdate();
        try {
            boolean oneVal = values.length == 1;
            if (oneVal) {
                Object v = values[0];
                if (null == v) return _unset(fieldExpr);
                else return _set(fieldExpr, v);
            }
            String[] sa = fieldExpr.split("(And|[,;\\s]+)");
            if (sa.length != values.length) {
                throw new IllegalArgumentException("number of fields doesn't match number of values");
            }
            List<Object> notNullList = new ArrayList<Object>();
            StringBuilder nullFields = new StringBuilder();
            StringBuilder notNullFields = new StringBuilder();
            for (int i = 0; i < values.length; ++i) {
                Object o = values[i];
                String f = sa[i];
                if (null != o) {
                    notNullList.add(o);
                    if (notNullFields.length() == 0) {
                        notNullFields.append(f);
                    } else {
                        notNullFields.append(",").append(f);
                    }
                } else {
                    if (nullFields.length() == 0) {
                        nullFields.append(f);
                    } else {
                        nullFields.append(",").append(f);
                    }
                }
            }
            String nf = nullFields.toString();
            String nnf = notNullFields.toString();
            if (nf.equals("")) return _set(fieldExpr, values);
            if (nnf.equals("")) return _unset(fieldExpr);
            new MorphiaBatchUpdates<T>(this)._set(nnf, notNullList.toArray())._unset(nf).commit();
            return (T) this;
        } finally {
            if (!noRefresh) {
                _h_Updated(refresh());
            }
        }
    }

    public <T extends Model> T _set(String fieldExpr, Object ... values) {
        new MorphiaBatchUpdates<T>(this)._set(fieldExpr, values).commit();
        return (T)this;
    }

    public <T extends Model> T _unset(String fieldExpr) {
        new MorphiaBatchUpdates<T>(this)._unset(fieldExpr).commit();
        return (T)this;
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
                "Please annotate your model with @org.mongodb.morphia.annotations.Entity annotation.");
    }

    public static <T extends Model> MorphiaQuery all(Class<T> cls) {
        return q(cls);
    }

    public static <T extends Model> MorphiaQuery all(Class<T> cls, DBCollection coll, Datastore ds) {
        return q(cls, coll, ds);
    }

    public static Model create(String name, Params params) {
        throw toBeEnhanced();
    }

    /**
     * Shortcut to createQuery
     *
     * @return
     */
    public static <T extends Model> MorphiaQuery q() {
        throw toBeEnhanced();
    }

    public static <T extends Model> MorphiaQuery q(Class<T> cls) {
        return new MorphiaQuery(cls);
    }

    public static <T extends Model> MorphiaQuery q(Class<T> cls, DBCollection coll, Datastore ds) {
        return new MorphiaQuery(cls, coll, ds);
    }

    public static <T extends Model> MorphiaQuery createQuery() {
        throw toBeEnhanced();
    }

    public static <T extends Model> MorphiaQuery createQuery(Class<T> cls) {
        return q(cls);
    }

    public static <T extends Model> MorphiaQuery createQuery(Class<T> cls, DBCollection coll, Datastore ds) {
        return q(cls, coll, ds);
    }

    public static <T extends Model> MorphiaQuery disableValidation() {
        throw toBeEnhanced();
    }

    public static long count() {
        throw toBeEnhanced();
    }

    public static long count(String keys, Object... params) {
        throw toBeEnhanced();
    }

    /**
     * Return a Set of distinct values for the given key
     *
     * @param key
     * @return a distinct set of key values
     */
    public static Set<?> _distinct(String key) {
        throw toBeEnhanced();
    }

    public static Long _max(String field) {
        throw toBeEnhanced();
    }

    public static AggregationResult groupMax(String field, String... groupKeys) {
        throw toBeEnhanced();
    }

    public static Long _min(String field) {
        throw toBeEnhanced();
    }

    public static AggregationResult groupMin(String field, String... groupKeys) {
        throw toBeEnhanced();
    }

    public static Long _average(String field) {
        throw toBeEnhanced();
    }

    public static AggregationResult groupAverage(String field,
            String... groupKeys) {
        throw toBeEnhanced();
    }

    public static Long _sum(String field) {
        throw toBeEnhanced();
    }

    public static AggregationResult groupSum(String field, String... groupKeys) {
        throw toBeEnhanced();
    }

    public static AggregationResult groupCount(String... groupKeys) {
        throw toBeEnhanced();
    }

    public static Map<String, Long> _cloud(String field) {
        throw toBeEnhanced();
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
        _removeFromCache();
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
        Map<String, String> blobKeys = __getBlobKeys();
        for (Map.Entry<String, String> entry: blobKeys.entrySet()) {
            bss(entry.getKey()).remove(entry.getValue());
        }
        deleteLegacyBlobs();
    }
    
    private void deleteLegacyBlobs() {
        GridFS gfs = MorphiaPlugin.gridFs();
        Pattern ptn = Pattern.compile(getIdAsStr());
        gfs.remove(new BasicDBObject("name", ptn));
    }

    protected void deleteBlobsInBatch(MorphiaQuery q) {
        q.retrievedFields(true, "__blobs");
        for (Model model : q.asList()) {
            model.deleteBlobs();
        }
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
        throw toBeEnhanced();
    }

    /**
     * Shortcut to createQuery()
     *
     * @return
     */
    public static <T extends Model> MorphiaQuery find() {
        throw toBeEnhanced();
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
        throw toBeEnhanced();
    }

    public static <T> List<T> findAll() {
        throw toBeEnhanced();
    }

    public static <T extends Model> T findById(Object id) {
        throw toBeEnhanced();
    }

    protected String _cacheKey() {
        return cacheKey(getClass(), getId());
    }

    protected static <T extends Model> String cacheKey(Class<T> cls, Object id) {
        return id + cls.getName();
    }

    protected static <T extends Model> T findById(Class<T> cls, Object id, boolean useFactory, boolean useCache, String expiration) {
        T e = null;
        String k = null;
        if (useCache) {
            k = cacheKey(cls, id);
            e = play.cache.Cache.get(k, cls);
        }
        if (null == e) {
            if (useFactory) {
                e = (T)MorphiaPlugin.MorphiaModelLoader.getFactory(cls).findById(id);
            } else {
                e = q(cls).filter("_id", processId_(id)).get();
            }
        }
        if (useCache && null != e) {
            play.cache.Cache.set(k, e, expiration);
        }
        return e;
    }

    public static <T extends Model> T findById(Class<T> cls, Object id, boolean useFactory) {
        return Model.findById(cls, id, useFactory, false, "");
    }

    /**
     * Shortcut to find(String, Object...)
     *
     * @param keys
     * @param keys
     * @return
     */
    public static <T extends Model> MorphiaQuery q(String keys, Object value) {
        throw toBeEnhanced();
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
        throw toBeEnhanced();
    }

    public static <T extends Model> MorphiaUpdateOperations createUpdateOperations() {
        throw toBeEnhanced();
    }

    /**
     * Alias of #updateOperations()
     * @param <T>
     * @return
     */
    public static <T extends Model> MorphiaUpdateOperations o() {
        throw toBeEnhanced();
    }

    // -- additional quick access method
    /**
     * Return the first element in the data storage. Return null if there is no
     * record found
     */
    public static <T extends Model> T get() {
        throw toBeEnhanced();
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
        throw toBeEnhanced();
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
        saveBlobs();
        Key<? extends Model> k = ds().save(this);
        if (isNew) {setSaved_();_h_Added();} else _h_Updated(this);
        return k;
    }

    public static <T extends Model> WriteResult insert(T entity) {
        Morphia morphia = MorphiaPlugin.morphia();
        DBObject o = morphia.toDBObject(entity);
        return entity.col().insert(o);
    }

    public static <T extends Model> WriteResult insert(T entity, WriteConcern concern) {
        Morphia morphia = MorphiaPlugin.morphia();
        DBObject o = morphia.toDBObject(entity);
        return entity.col().insert(o, concern);
    }

    public static <T extends Model> WriteResult insert(List<T> entities) {
        if (entities.isEmpty()) return null;
        T t = entities.get(0);
        List<DBObject> l = new ArrayList<DBObject>(entities.size());
        Morphia morphia = MorphiaPlugin.morphia();
        boolean populateId = !MorphiaPlugin.getIdType().isObjectId();
        for (T entity: entities) {
            if (populateId) entity.setId(IdGenerator.generateId(entity));
            l.add(morphia.toDBObject(entity));
        }
        return t.ds().getCollection(t.getClass()).insert(l);
    }

    public static <T extends Model> WriteResult insert(List<T> entities, WriteConcern concern) {
        if (entities.isEmpty()) return null;
        T t = entities.get(0);
        List<DBObject> l = new ArrayList<DBObject>(entities.size());
        Morphia morphia = MorphiaPlugin.morphia();
        for (T entity: entities) {
            l.add(morphia.toDBObject(entity));
        }
        return t.col().insert(l, concern);
    }

    public static <T extends Model> WriteResult insert(T[] entities, WriteConcern concern) {
        if (entities.length == 0) return null;
        return insert(concern, entities);
    }

    public static <T extends Model> WriteResult insert(T... entities) {
        if (entities.length == 0) return null;
        T t = entities[0];
        List<DBObject> l = new ArrayList<DBObject>(entities.length);
        Morphia morphia = MorphiaPlugin.morphia();
        for (T entity: entities) {
            l.add(morphia.toDBObject(entity));
        }
        return t.col().insert(l);
    }

    public static <T extends Model> WriteResult insert(WriteConcern writeConcern, T[] entities) {
        if (entities.length == 0) return null;
        T t = entities[0];
        List<DBObject> l = new ArrayList<DBObject>(entities.length);
        Morphia morphia = MorphiaPlugin.morphia();
        for (T entity: entities) {
            l.add(morphia.toDBObject(entity));
        }
        return t.col().insert(l, writeConcern);
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
        if (null == __blobs) {
            __blobs = C.newMap();
        }
        boolean saveBlobKeys = loadBlobs();
        h_Loaded();
        MorphiaPlugin.onLifeCycleEvent(MorphiaEvent.LOADED, this);
        postEvent_(MorphiaEvent.LOADED, this);
        if (saveBlobKeys) {
            _update(true, "__blobs", __blobs);
        }
    }

    /**
     * for PlayMorphia internal usage only
     */
    protected void h_Loaded() {
        // for enhancer usage
    }

    protected boolean _cacheEnabled() {
        return false;
    }

    protected String _cacheExpiration() {
        return null;
    }

    protected void _removeFromCache() {
        if (!_cacheEnabled()) return;
        String cacheKey = _cacheKey();
        play.cache.Cache.delete(cacheKey);
    }

    protected void _addToCache() {
        if (!_cacheEnabled()) return;
        String cacheKey = _cacheKey();
        String expiration = _cacheExpiration();
        play.cache.Cache.add(cacheKey, this, expiration);
    }

    /**
     * for PlayMorphia internal usage only
     */
    private void _h_Added() {
        _addToCache();
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

    protected void _updateCache(Model updated) {
        if (!_cacheEnabled()) return;
        String cacheKey = _cacheKey();
        play.cache.Cache.replace(cacheKey, null == updated ? this : updated, _cacheExpiration());
    }

    /**
     * for PlayMorphia internal usage only
     */
    private void _h_Updated(Model updated) {
        _updateCache(updated);
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

    protected boolean loadBlobs() {
        // used by enhander
        return false;
    }
    
    protected BlobStorageService bss(String fieldName) {
        throw toBeEnhanced();
    }

    private Map<String, String> __blobs = C.newMap();
    
    protected Map<String, String> __getBlobKeys() {
        return C.map(__blobs);
    }

    protected void __setBlobKey(String field, String blobKey) {
        E.NPE(field, blobKey);
        __blobs.put(field, blobKey);
    }

    protected String __getBlobKey(String field) {
        E.NPE(field);
        return __blobs.get(field);
    }

    @Transient
    transient protected final Map<String, Boolean> blobFieldsTracker = C.newMap();
    protected final boolean __blobChanged(String fieldName) {
        return (blobFieldsTracker.containsKey(fieldName) && blobFieldsTracker.get(fieldName));
    }
    
    protected final void __setBlobChanged(String fieldName) {
        blobFieldsTracker.put(fieldName, true);
    }

    @Deprecated
    public String getBlobFileName(String fieldName) {
        return getBlobFileName(getId(), fieldName);
    }

    @Deprecated
    public static String getBlobFileName(Object id, String fieldName) {
        return String.format("%s_%s", StringUtils.capitalize(fieldName), id);
    }
    
    protected void removeBlobs(MorphiaQuery q, String fieldName) {
        q.retrievedFields(true, "__blobs");
        for (Model model : q.asList()) {
            String key = model.__getBlobKeys().get(fieldName);
            if (null != key) {
                bss(fieldName).remove(key);
            }
        }
    }

    // -- auto timestamp methods
    public long _getCreated() {
        throw toBeEnhanced("Please annotate model with @AutoTimestamp annotation");
    }

    public long _getModified() {
        throw toBeEnhanced("Please annotate model with @AutoTimestamp annotation");
    }

    private static void postEvent_(MorphiaEvent event, Object context) {
        if (MorphiaPlugin.postPluginEvent) PlayPlugin.postEvent(event.getId(), context);
    }
    
    private static UnsupportedException toBeEnhanced() {
        return E.unsupport("Please annotate your model with @org.mongodb.morphia.annotations.Entity annotation.");
    } 
    
    private static UnsupportedException toBeEnhanced(String msg, Object... args) {
        return E.unsupport(msg, args);
    }

    public static class MorphiaUpdateOperations {
        public static Datastore ds() {
            return MorphiaPlugin.ds();
        }

        private UpdateOpsImpl<? extends Model> u_;
        private Class <? extends Model> c_;

        public UpdateOperations<? extends Model> getMorphiaUpdateOperations() {
            return u_;
        }

        public DBObject getUpdateOperationsObject() {
            return u_.getOps();
        }

        public DBCollection col() {
            return ds().getCollection(c_);
        }

        private MorphiaUpdateOperations() {
            // constructor for clone() usage
        }

        public MorphiaUpdateOperations(Class<? extends Model> clazz) {
            u_ = new UpdateOpsImpl(clazz, ((DatastoreImpl)ds()).getMapper());
            c_ = clazz;
        }

        private boolean multi = true;
        public MorphiaUpdateOperations multi(boolean multi) {
            this.multi = multi;
            return this;
        }
        public boolean multi() {
            return multi;
        }

        public MorphiaUpdateOperations validation(boolean validate) {
            if (validate) u_.enableValidation();
            else u_.disableValidation();
            return this;
        }

        public MorphiaUpdateOperations enableValidation() {
            return validation(true);
        }

        public MorphiaUpdateOperations disableValidation() {
            return validation(false);
        }

        public MorphiaUpdateOperations isolate(boolean isolate) {
            if (isolate) u_.isolated();
            else {
                throw E.unsupport("Morphia does not support set isolated to false");
            }
            return this;
        }

        public MorphiaUpdateOperations enableIsolate() {
            return isolate(true);
        }

        public MorphiaUpdateOperations disableIsolate() {
            return isolate(false);
        }

        public MorphiaUpdateOperations isolated() {
            return enableIsolate();
        }

        public MorphiaUpdateOperations add(String fieldExpr, Object value) {
            u_.add(fieldExpr, value);
            return this;
        }

        public MorphiaUpdateOperations add(String fieldExpr, Object value, boolean addDups) {
            u_.add(fieldExpr, value, addDups);
            return this;
        }

        public MorphiaUpdateOperations addAll(String fieldExpr, List<?> values, boolean addDups) {
            u_.addAll(fieldExpr, values, addDups);
            return this;
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj);    //To change body of overridden methods use File | Settings | File Templates.
        }

        public MorphiaUpdateOperations dec(String fieldExpr) {
            E.invalidArgIf(S.empty(fieldExpr));;
            if (fieldExpr.startsWith("by"))
                fieldExpr = fieldExpr.substring(2);
            String[] keys = fieldExpr.split("(And|[,;\\s]+)");

            for (int i = 0; i < keys.length; ++i) {
                StringBuilder sb = new StringBuilder(keys[i]);
                sb.setCharAt(0, Character.toLowerCase(sb.charAt(0)));
                u_.dec(sb.toString());
            }
            return this;
        }

        public MorphiaUpdateOperations inc(String fieldExpr) {
            E.invalidArgIf(S.empty(fieldExpr));;
            if (fieldExpr.startsWith("by"))
                fieldExpr = fieldExpr.substring(2);
            String[] keys = fieldExpr.split("(And|[,;\\s]+)");

            for (int i = 0; i < keys.length; ++i) {
                StringBuilder sb = new StringBuilder(keys[i]);
                sb.setCharAt(0, Character.toLowerCase(sb.charAt(0)));
                u_.inc(sb.toString());
            }
            return this;
        }

        public MorphiaUpdateOperations inc(String fieldExpr, Number... values) {
            E.invalidArgIf(S.empty(fieldExpr) || values.length == 0);;
            if (fieldExpr.startsWith("by"))
                fieldExpr = fieldExpr.substring(2);
            String[] keys = fieldExpr.split("(And|[,;\\s]+)");

            E.invalidArgIf((values.length != 1) && (keys.length != values.length), 
                "Query key number does not match the params number");

            Number oneVal = values.length == 1 ? values[0] : null;

            for (int i = 0; i < keys.length; ++i) {
                StringBuilder sb = new StringBuilder(keys[i]);
                sb.setCharAt(0, Character.toLowerCase(sb.charAt(0)));
                u_.inc(sb.toString(), oneVal == null ? values[i] : oneVal);
            }
            return this;
        }

        public MorphiaUpdateOperations removeAll(String fieldExpr, Object value) {
            u_.removeAll(fieldExpr, value);
            return this;
        }

        public MorphiaUpdateOperations removeAll(String fieldExpr, List<?> values) {
            u_.removeAll(fieldExpr, values);
            return this;
        }

        public MorphiaUpdateOperations removeFirst(String fieldExpr) {
            u_.removeFirst(fieldExpr);
            return this;
        }

        public MorphiaUpdateOperations removeLast(String fieldExpr) {
            u_.removeLast(fieldExpr);
            return this;
        }

        public MorphiaUpdateOperations set(String fieldExpr, Object... values) {
            E.invalidArgIf(S.empty(fieldExpr) || values.length == 0);
            if (fieldExpr.startsWith("by"))
                fieldExpr = fieldExpr.substring(2);
            String[] keys = fieldExpr.split("(And|[,;\\s]+)");

            E.invalidArgIf((values.length != 1) && (keys.length != values.length), 
                "Query key number does not match the params number");

            Object oneVal = values.length == 1 ? values[0] : null;

            for (int i = 0; i < keys.length; ++i) {
                StringBuilder sb = new StringBuilder(keys[i]);
                sb.setCharAt(0, Character.toLowerCase(sb.charAt(0)));
                u_.set(sb.toString(), oneVal == null ? values[i] : oneVal);
            }
            return this;
        }

        public MorphiaUpdateOperations unset(String fieldExpr) {
            u_.unset(fieldExpr);
            return this;
        }

        public <T> T updateFirst(MorphiaQuery q) {
            return (T)findAndModify(q);
        }

        public <T> T updateFirst(String query, Object... params) {
            MorphiaQuery q = new MorphiaQuery(c_).findBy(query, params);
            return (T)findAndModify(q);
        }

        public <T> T findAndModify(MorphiaQuery q) {
            return (T)ds().findAndModify((Query)q.getMorphiaQuery(), (UpdateOperations)u_);
        }

        public <T> T findAndModify(String query, Object... params) {
            MorphiaQuery q = new MorphiaQuery(c_).findBy(query, params);
            return (T)findAndModify(q);
        }

        public <T> T updateFirst(MorphiaQuery q, boolean oldVersion) {
            return (T)findAndModify(q, oldVersion);
        }

        public <T> T updateFirst(boolean oldVersion, String query, Object... params) {
            MorphiaQuery q = new MorphiaQuery(c_).findBy(query, params);
            return (T)findAndModify(q, oldVersion);
        }

        public <T> T findAndModify(MorphiaQuery q, boolean oldVersion) {
            return (T)ds().findAndModify((Query)q.getMorphiaQuery(), (UpdateOperations)u_, oldVersion);
        }

        public <T> T findAndModify(boolean oldVersion, String query, Object... params) {
            MorphiaQuery q = new MorphiaQuery(c_).findBy(query, params);
            return (T)findAndModify(q, oldVersion);
        }

        public <T> T updateFirst(MorphiaQuery q, boolean oldVersion, boolean createIfMissing) {
            return (T)findAndModify(q, oldVersion, createIfMissing);
        }

        public <T> T findAndModify(MorphiaQuery q, boolean oldVersion, boolean createIfMissing) {
            return (T)ds().findAndModify((Query)q.getMorphiaQuery(), (UpdateOperations)u_, oldVersion, createIfMissing);
        }

        public <T> UpdateResults update(MorphiaQuery q) {
            return ds().update((Query<T>)q.getMorphiaQuery(), (UpdateOperations<T>)u_);
        }

        public <T> UpdateResults update(String query, Object... params) {
            MorphiaQuery q = new MorphiaQuery(c_).findBy(query, params);
            return ds().update((Query<T>)q.getMorphiaQuery(), (UpdateOperations<T>)u_);
        }

        public <T> UpdateResults update(Model entity) {
            return update("_id", entity.getId());
        }

        private <T> UpdateResults update(Query<T> q) {
            return ds().update(q, (UpdateOperations<T>)u_);
        }

        public <T> UpdateResults updateAll() {
            return ds().update((QueryImpl) ds().createQuery(c_), (UpdateOperations<T>)u_);
        }

    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static class MorphiaQuery {
        public static Datastore ds() {
            return MorphiaPlugin.ds();
        }

        private Query<? extends Model> q_;
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
      q_ = ds().createQuery(clazz);
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
            q_ = new QueryImpl(clazz, coll, ds).offset(offset).limit(limit);
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
                E.unexpected(e);
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
            E.invalidArgIf(null == query);
            if (query.startsWith("by")) {
                query = query.substring(2);
            }
            if (null == params) {
                params = new Object[]{null};
            }
            String[] keys = query.split("(And|[,;\\s]+)");

            E.invalidArgIf((params.length != 1) && (keys.length != params.length), 
                "Query key number does not match the params number");

            Object oneVal = params.length == 1 ? params[0] : null;

            for (int i = 0; i < keys.length; ++i) {
                StringBuilder sb = new StringBuilder(keys[i]);
                sb.setCharAt(0, Character.toLowerCase(sb.charAt(0)));
                q_.filter(sb.toString(), params.length > 1 ? params[i] : oneVal);
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
            key = MorphiaPlugin.mongoColName(c_, key);
            return new HashSet(col().distinct(key.toString(), getQueryObject()));
        }

        public Map<String, Long> cloud(String field) {
            field = MorphiaPlugin.mongoColName(c_, field);
            String map = String.format("function() {if (!this.%1$s) return; for (index in this.%1$s) emit(this.%1$s[index], 1);}", field);
            String reduce = "function(previous, current) {var count = 0; for (index in current) count += current[index]; return count;}";
            MapReduceCommand cmd = new MapReduceCommand(col(), map, reduce, null, MapReduceCommand.OutputType.INLINE, q_.getQueryObject());
            MapReduceOutput out = col().mapReduce(cmd);
            Map<String, Long> m = new HashMap<String, Long>();
            for (Iterator<DBObject> itr = out.results().iterator(); itr.hasNext(); ) {
                DBObject dbo = itr.next();
                m.put((String) dbo.get("_id"), ((Double) dbo.get("value")).longValue());
            }
            return m;
        }

        /**
         *
         * @param groupKeys
         *            could be either "f1Andf2.." or "f1 f2" or "f1,f2"
         * @return
         */
        public List<BasicDBObject> group(String groupKeys, DBObject initial,
                String reduce, String finalize) {
            DBObject key = new BasicDBObject();
            if (!S.empty(groupKeys)) {
                if (groupKeys.startsWith("by"))
                    groupKeys = groupKeys.substring(2);
                String[] sa = groupKeys.split("(And|[\\s,;]+)");
                for (String s : sa) {
                    key.put(MorphiaPlugin.mongoColName(c_, s), true);
                }
            }
            return (List<BasicDBObject>) ds().getCollection(c_).group(key,
                    q_.getQueryObject(), initial, reduce, finalize);
        }

        private AggregationResult aggregate_(String field, String mappedField, DBObject initial,
                Long initVal, String reduce, String finalize,
                String... groupKeys) {
            if (null == initial)
                initial = new BasicDBObject();
            initial.put(mappedField, initVal);
            return new AggregationResult(group(S.join(",", groupKeys),
                    initial, reduce, finalize), field, c_);
        }

        public AggregationResult groupMax(String field, String... groupKeys) {
            String mappedField = MorphiaPlugin.mongoColName(c_, field);
            String reduce = String
                    .format("function(obj, prev){if (obj.%s > prev.%s) prev.%s = obj.%s}",
                            mappedField, mappedField, mappedField, mappedField);
            return aggregate_(field, mappedField, null, Long.MIN_VALUE + 1, reduce, null,
                    groupKeys);
        }

        public Long max(String maxField) {
            return groupMax(maxField).getResult();
        }

        public AggregationResult groupMin(String field, String... groupKeys) {
            String mappedField = MorphiaPlugin.mongoColName(c_, field);
            String reduce = String
                    .format("function(obj, prev){if (obj.%s < prev.%s) prev.%s = obj.%s}",
                            mappedField, mappedField, mappedField, mappedField);
            return aggregate_(field, mappedField, null, Long.MAX_VALUE - 1, reduce, null,
                    groupKeys);
        }

        public Long min(String minField) {
            return groupMin(minField).getResult();
        }

        public AggregationResult groupAverage(String field, String... groupKeys) {
            String mappedField = MorphiaPlugin.mongoColName(c_, field);
            DBObject initial = new BasicDBObject();
            initial.put("__count", 0);
            initial.put("__sum", 0);
            String reduce = String.format(
                    "function(obj, prev){prev.__count++; prev.__sum+=obj.%s;}",
                    mappedField);
            String finalize = String.format(
                    "function(prev) {prev.%s = prev.__sum / prev.__count;}",
                    mappedField);
            return aggregate_(field, mappedField, initial, 0L, reduce, finalize, groupKeys);
        }

        public Long average(String field) {
            return groupAverage(field).getResult();
        }

        public AggregationResult groupSum(String field, String... groupKeys) {
            String mappedField = MorphiaPlugin.mongoColName(c_, field);
            String reduce = String.format(
                    "function(obj, prev){prev.%s+=obj.%s;}", mappedField, mappedField);
            return aggregate_(field, mappedField, null, 0L, reduce, null, groupKeys);
        }

        public Long sum(String field) {
            return groupSum(field).getResult();
        }

        public AggregationResult groupCount(String... groupKeys) {
            String mappedField = MorphiaPlugin.mongoColName(c_, "_id");
            String reduce = String.format("function(obj, prev){prev.%s++;}", mappedField);
            return aggregate_("_id", mappedField, null, 0L, reduce, null, groupKeys);
        }

        public <T extends Model> Iterable<T> fetchEmptyEntities() {
            return (Iterable<T>) q_.fetchEmptyEntities();
        }

        public <T extends Model> FieldEnd<? extends Query<T>> field(String field) {
            return (FieldEnd<? extends Query<T>>) q_.field(field);
        }

    public <T extends Model> FieldEnd<? extends Query<T>> search(String text) {
      return (FieldEnd<? extends Query<T>>) q_.search(text);
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

    @Deprecated
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
            mq.q_ = q_.cloneQuery();
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
    @Target({ ElementType.TYPE })
    public @interface NoAutoTimestamp {
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
     * @see //groups.google.com/d/topic/play-framework/hPWJCvefPoI/discussion
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
