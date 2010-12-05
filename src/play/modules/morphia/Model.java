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
import com.google.code.morphia.annotations.PostLoad;
import com.google.code.morphia.annotations.PostPersist;
import com.google.code.morphia.annotations.PrePersist;
import com.google.code.morphia.annotations.Reference;
import com.google.code.morphia.annotations.Transient;
import com.google.code.morphia.query.Criteria;
import com.google.code.morphia.query.CriteriaContainer;
import com.google.code.morphia.query.CriteriaContainerImpl;
import com.google.code.morphia.query.FieldEnd;
import com.google.code.morphia.query.Query;
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
    	ds().delete(this);
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
                            Collection<Model> l = new ArrayList<Model>();
                            if (SortedSet.class.isAssignableFrom(field.getType())) {
                                l = new TreeSet<Model>();
                            } else if (Set.class.isAssignableFrom(field.getType())) {
                                l = new HashSet<Model>();
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
                                        Query<Model> q = ds().createQuery(c).filter(keyName, processId_(_id));
                                        try {
                                            l.add(q.get());
                                        } catch (Exception e) {
                                            Validation.addError(name + "." + field.getName(),
                                                    "validation.notFound", _id);
                                        }
                                    }
                                }
                            } else {
                                Logger.debug("multiple embedded objects not supported yet");
                            }
                            bw.set(field.getName(), o, l);
                            Logger.debug("Entity[%1$s]'s field[%2$s] has been set to %3$s", o.getClass().getName(), field.getName(), l);
                        } else {
                            String[] ids = params.get(name + "." + field.getName() + "." + keyName);
                            if (ids != null && ids.length > 0 && !ids[0].equals("")) {
                                params.remove(name + "." + field.getName() + "." + keyName);
                                Query<Model> q = ds().createQuery(c).filter(keyName, processId_(ids[0]));
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
                            } else {
                                String name0 = name + "." + field.getName();
                                Object o0 = Model.create(field.getType(), name0, params, null);
                                bw.set(field.getName(), o, o0);
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
     * If user defined customized \@Id field, it's better to override
     * this method for the sake of performance. Otherwise framework will
     * use reflection to get the value
     * 
     * @return
     */
    public Object getId() {
        return null;
    }
    
    public final void setId(Object id) {
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
    
    @SuppressWarnings("unused")
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
    
    // -- helper utilities
    @Transient
    private transient boolean saved_ = false;
    /**
     * A utility method determine whether this entity is a newly
     * constructed object in memory or represents a data from mongodb
     * @return true if this is a memory object which has not been saved to db yet, false otherwise
     */
    public final boolean isNew() {
        return !saved_;
    }
    @SuppressWarnings("unused")
	@PostLoad
    @PostPersist
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

    public static <T extends Model> MorphiaQuery createQuery() {
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

    @SuppressWarnings("unchecked")
	public <T extends Model> T delete() {
        _delete();
        return (T) this;
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

    public static <T extends Model> MorphiaQuery find() {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }
    
    /**
     * JPA style find method 
     * @param keys should be in style of "byKey1[AndKey2[AndKey3...]]"
     * @param params number should either be one or the same number of keys
     * @return
     */
    public static <T extends Model> MorphiaQuery find(String keys, Object... params) {
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

    public static <T extends Model> MorphiaQuery filter(String property, Object value) {
        throw new UnsupportedOperationException(
                "Please annotate your model with @com.google.code.morphia.annotations.Entity annotation.");
    }
    
    // -- additional quick access method
    /**
     * Return the first element in the data storage. Return null if there is no record found
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
     * Return MongoDB DB instance
     * 
     * @return
     */
    public static DB db() {
        return ds().getDB();
    }
    
    /**
     * Save and return this entity
     * @param <T>
     * @return
     */
    @SuppressWarnings("unchecked")
	public <T extends Model> T save() {
        ds().save(this);
        return (T)this;
    }

    /**
     * Save and return Morphia Key
     * @return
     */
    public Key<? extends Model> save2() {
        return ds().save(this);
    }

    
    @SuppressWarnings({ "rawtypes", "unchecked" })
	public static class MorphiaQuery  {
        public static Datastore ds() {
            return MorphiaPlugin.ds();
        }

        private Query<? extends Model> q_;
        
        // constructor for clone() usage
        private MorphiaQuery() {}
        
        public MorphiaQuery(Class<? extends Model> clazz) {
            //super(clazz, ds().getCollection(clazz), ds());
            q_ = ds().createQuery(clazz);
        }

		public MorphiaQuery(Class<? extends Model> clazz, DBCollection coll, Datastore ds) {
            //super(clazz, coll, ds);
            q_ = new QueryImpl(clazz, coll, ds);
        }

        public MorphiaQuery(Class<? extends Model> clazz, DBCollection coll, Datastore ds, int offset, int limit) {
            //super(clazz, coll, ds, offset, limit);
            q_ = new QueryImpl(clazz, coll, ds, offset, limit);
        }

        public long delete() {
        	long l = count();
            ds().delete(this);
            return l;
        }

        /**
         * Alias of countAll()
         * @return
         */
        public long count() {
            return q_.countAll();
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
         * @param position Position of the first element
         * @return A new query
         */
        public <T> MorphiaQuery from(int position) {
            q_.offset(position);
            return this;
        }

        /**
         * Retrieve all results of the query
         * 
         * This is a correspondence to JPAQuery's fetch(), which however, 
         * used as another method signature of Morphia Query
         * @return A list of entities
         */
        public <T extends Model> List<T> fetchAll() {
        	return (List<T>) q_.asList();
        }

        /**
         * Retrieve results of the query
         * @param max Max results to fetch
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
            return (List<T>) q_.offset((page - 1) * length).limit(length).asList();
        }


        // ---------------------------------------------------------------------------
        // Morphia Query, QueryResults, Criteria, CriteriaContainer interface
        // ---------------------------------------------------------------------------

        // for the sake of enhancement
        public Model _get() { return (Model)q_.get(); }
        
        public <T extends Model> T get() {return (T) q_.get();}

		public <T extends Model> MorphiaQuery filter(String condition, Object value) {
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
			return ((Query<T>)q_).asKeyList();
		}

		public <T extends Model> Iterable<T> fetch() {
			return (Iterable<T>) q_.fetch();
		}

		public <T extends Model> Iterable<T> fetchEmptyEntities() {
			return (Iterable<T>) q_.fetchEmptyEntities();
		}

		public <T extends Model> FieldEnd<? extends Query<T>> field(String field) {
			return (FieldEnd<? extends Query<T>>) q_.field(field);
		}

		public <T extends Model> Iterable<Key<T>> fetchKeys() {
			return ((Query<T>)q_).fetchKeys();
		}

		public <T extends Model> FieldEnd<? extends CriteriaContainerImpl> criteria(String field) {
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

		public <T extends Model> MorphiaQuery disableTimeout() {
			q_.disableTimeout();
			return this;
		}

		public <T extends Model> MorphiaQuery enableTimeout() {
			q_.enableTimeout();
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

}
