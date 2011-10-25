package play.modules.morphia;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bson.types.ObjectId;

import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.data.binding.Binder;
import play.db.Model.Factory;
import play.exceptions.ConfigurationException;
import play.exceptions.UnexpectedException;
import play.modules.morphia.Model.Datasource;
import play.modules.morphia.Model.MorphiaQuery;
import play.modules.morphia.MorphiaEvent.IMorphiaEventHandler;
import play.modules.morphia.utils.StringUtil;

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
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.gridfs.GridFS;

/**
 * The plugin for the Morphia module.
 * 
 * @author greenlaw110@gmail.com
 */
public class MorphiaPlugin extends PlayPlugin {
    public static final String VERSION = "1.2.4";
    
    public static void info(String msg, Object... args) {
        Logger.info(msg_(msg, args));
    }
    
    public static void debug(String msg, Object... args) {
        Logger.debug(msg_(msg, args));
    }

    public static void warn(String msg, Object... args) {
        Logger.warn(msg_(msg, args));
    }

    public static void warn(Throwable t, String msg, Object... args) {
        Logger.warn(t, msg_(msg, args));
    }

    public static void error(String msg, Object... args) {
        Logger.error(msg_(msg, args));
    }

    public static void error(Throwable t, String msg, Object... args) {
        Logger.error(t, msg_(msg, args));
    }

    public static void fatal(String msg, Object... args) {
        Logger.fatal(msg_(msg, args));
    }

    public static void fatal(Throwable t, String msg, Object... args) {
        Logger.fatal(t, msg_(msg, args));
    }

    private static String msg_(String msg, Object... args) {
        return String.format("MorphiaPlugin-" + VERSION + "> %1$s",
                String.format(msg, args));
    }

    public static final String PREFIX = "morphia.db.";
    
    public static final String DEFAULT_DS_NAME = "default";
    
    private MorphiaEnhancer e_ = new MorphiaEnhancer();
    private static boolean configured_ = false;

    public static boolean configured() {
        return configured_;
    }

    public static enum IdType {
        Long, ObjectId
    }

    private static IdType idType_ = IdType.ObjectId;

    public static IdType getIdType() {
        return idType_;
    }

    private static final ConcurrentMap<String, Datastore> 
        dataStores_ = new ConcurrentHashMap<String, Datastore>();

    @Deprecated
    public static Datastore ds() {
        return dataStores_.get(DEFAULT_DS_NAME);
    }

    public static Datastore ds(String datasourceName) {
        Datastore ds = dataStores_.get(datasourceName);
        if (ds == null) {
            
            if (DEFAULT_DS_NAME.equals(datasourceName)) {
                throw new RuntimeException(
                        "There is no default datasource configured.  Please check that the application.conf file " +
                        "contains default morphia configuration or that the @Datasource annotations in your model " +
                        "classes match the names of the configured morphia datasources ");
            } else {
                ds = dataStores_.get(DEFAULT_DS_NAME);
                
                if (ds == null) {
                    throw new RuntimeException(
                            "Application.conf does not contain a morphia configuration named " + datasourceName + 
                            ", nor does it contain a default morphia configuration");
                }
            }
        }
        
        return ds;
    }
    
    private static GridFS gridfs_ = null;

    public static GridFS gridFs() {
        return gridfs_;
    }

    private static final ConcurrentMap<String, Morphia> 
        morphias_ = new ConcurrentHashMap<String, Morphia>();
    
    public static Morphia morphia(String datasourceName) {
        return morphias_.get(datasourceName);
    }

    
    @Override
    public void enhance(ApplicationClass applicationClass) throws Exception {
        //onConfigurationRead(); // ensure configuration be read before enhancement
        initIdType_();
        e_.enhanceThisClass(applicationClass);
    }
    
    private static List<IMorphiaEventHandler> eventHandlers_ = new ArrayList<IMorphiaEventHandler>();
    
    public static void registerEventHandler(IMorphiaEventHandler handler) {
        if (null == handler) throw new NullPointerException();
        if (!eventHandlers_.contains(handler)) eventHandlers_.add(handler);
    }
    
    void onLifeCycleEvent(MorphiaEvent event, Model model) {
        for (IMorphiaEventHandler h: eventHandlers_) {
            event.invokeOn(h, model);
        }
    }
    
    void onBatchLifeCycleEvent(MorphiaEvent event, MorphiaQuery query) {
        for (IMorphiaEventHandler h: eventHandlers_) {
            event.invokeOn(h, query);
        }
    }

    /*
     * Connect using conf - morphia.db.host=host1,host2... -
     * morphia.db.port=port1,port2...
     */
    private final Mongo connect_(String host, String port) {
        String[] ha = host.split("[,\\s;]+");
        String[] pa = port.split("[,\\s;]+");
        int len = ha.length;
        if (len != pa.length)
            throw new ConfigurationException(
                    "host and ports number does not match");
        if (1 == len) {
            try {
                return new Mongo(ha[0], Integer.parseInt(pa[0]));
            } catch (Exception e) {
                throw new ConfigurationException(
                        String.format("Cannot connect to mongodb at %s:%s", host, port));
            }
        }
        List<ServerAddress> addrs = new ArrayList<ServerAddress>(ha.length);
        for (int i = 0; i < len; ++i) {
            try {
                addrs.add(new ServerAddress(ha[i], Integer.parseInt(pa[i])));
            } catch (Exception e) {
                error(e, "Error creating mongo connection to %s:%s", host, port);
            }
        }
        if (addrs.isEmpty()) {
            throw new ConfigurationException(
                    "Cannot connect to mongodb: no replica can be connected");
        }
        return new Mongo(addrs);
    }

    /*
     * Connect using conf morphia.db.seeds=host1[:port1];host2[:port2]...
     */
    private final Mongo connect_(String seeds) {
        String[] sa = seeds.split("[;,\\s]+");
        List<ServerAddress> addrs = new ArrayList<ServerAddress>(sa.length);
        for (String s : sa) {
            String[] hp = s.split(":");
            if (0 == hp.length)
                continue;
            String host = hp[0];
            int port = 27017;
            if (hp.length > 1) {
                port = Integer.parseInt(hp[1]);
            }
            try {
                addrs.add(new ServerAddress(host, port));
            } catch (UnknownHostException e) {
                error(e, "error creating mongo connection to %s:%s", host, port);
            }
        }
        if (addrs.isEmpty()) {
            throw new ConfigurationException(
                    "Cannot connect to mongodb: no replica can be connected");
        }
        return new Mongo(addrs);
    }

    @Override
    public void onConfigurationRead() {
        if (configured_)
            return;
        debug("Morphia> reading configuration");
        Properties c = Play.configuration;

        getDatasourceNames();
        setupDatasources();
        setupGridFS();
        
        if (c.containsKey("morphia.id.type")) {
            Logger.debug("Morphia> reading id type...");
            String s = c.getProperty("morphia.id.type");
            try {
                idType_ = IdType.valueOf(s);
                Logger.debug("ID Type set to : %1$s", idType_.name());
                if ("1.2beta".equals(VERSION) && idType_ == IdType.Long) {
                    Logger.warn("Caution: Using reference in your model " +
                            "entities might cause problem when you ID type " +
                            "set to Long. " +
                            "Check http://groups.google.com/group/morphia/browse_thread/thread/bdd51121c2845973");
                }
            } catch (Exception e) {
                Logger.warn(
                        e,
                        "Error configure morphia id type: %1$s. Id type set to default: ObjectId.",
                        s);
            }
        }
        configured_ = true;
    }
    
    
    private static final Set<String> datasourceNames_ = new HashSet<String>();
    private final Pattern namedDatasourceKeyPattern = 
            Pattern.compile(PREFIX + "([a-zA-Z0-9]+)\\.(host|port|name|username|password|seeds)");
    
    private void getDatasourceNames() {
        Properties c = Play.configuration;
        Enumeration<String> propertyNames = (Enumeration<String>)c.propertyNames();
        while(propertyNames.hasMoreElements()) {
            String propertyName =  propertyNames.nextElement();
            
            Matcher matcher = namedDatasourceKeyPattern.matcher(propertyName);
            if (matcher.matches()) {
                String datasourceName = matcher.group(1);
                
                if (null != datasourceName && 
                        !"".equals(datasourceName) && 
                        !"collection".equals(datasourceName)) {
                    
                    datasourceNames_.add(datasourceName);
                }
            }
        }
    }
    
    private void setupDatasources() {
        if (!datasourceNames_.isEmpty()) {
            
            for (String dsName : datasourceNames_) {
                initializeDatasource(dsName);
            }
        
            Logger.debug("Morphia datasource keys found in application.conf %s", datasourceNames_);
        }
        
        //should only load this if there are no named datasources or if there are named datasources,
        //then there needs to be properties defined.  Should not load default properties if named
        //datasources are already loaded
        if (containsDefaultDatasourceConfiguration()) {
            initializeDatasource(null);
        }
    }
    
    private final Pattern defaultDatasourceKeyPattern = 
            Pattern.compile(PREFIX + "(host|port|name|username|password|seeds)");
    
    private boolean containsDefaultDatasourceConfiguration() {
        Properties c = Play.configuration;
        Enumeration<String> propertyNames = (Enumeration<String>)c.propertyNames();
        
        while(propertyNames.hasMoreElements()) {
            String propertyName =  propertyNames.nextElement();
            
            Matcher matcher = defaultDatasourceKeyPattern.matcher(propertyName);
            if (matcher.matches()) {
                return true;
            }
        }
        
        return false;
    }
    

    private static final ConcurrentMap<String, Mongo> mongos_ = 
            new ConcurrentHashMap<String, Mongo>();
    
    private static final ConcurrentMap<String, DB> dbs_ = 
            new ConcurrentHashMap<String, DB>();
    
    private synchronized void initializeDatasource(String datastoreName) {
        Properties c = Play.configuration;
        String prefix = (null == datastoreName || "".equals(datastoreName)) ? PREFIX : PREFIX + datastoreName + ".";
        String dsKey = (null == datastoreName || "".equals(datastoreName)) ? DEFAULT_DS_NAME : datastoreName;
        
        Logger.debug("Initializing %s morphia datasource", dsKey);
        
        Mongo  mongo = null;
        
        String seeds = c.getProperty(prefix + "seeds");
        if (!StringUtil.isEmpty(seeds)) {
            Logger.debug("Connecting to Mongo replica set %s", seeds);
            mongo = connect_(seeds);
        } else {
            String host = c.getProperty(prefix + "host", "localhost");
            String port = c.getProperty(prefix + "port", "27017");
            Logger.debug("Connecting to Mongo %s:%s ", host, port);
            mongo = connect_(host, port);
        }
  
        if (null == mongo) {
            throw new ConfigurationException("Morphia datasource name not configured: " + dsKey);
         } else {
             mongos_.put(dsKey, mongo);
         }
        
        String dbName = c.getProperty(prefix + "name");
        if (null == dbName) {
            Logger.warn("mongodb name not configured! using [test] db");
            dbName = "test";
        }
        
        DB db = mongo.getDB(dbName);
        if (c.containsKey(prefix + "username")
                && c.containsKey(prefix + "password")) {
            String username = c.getProperty(prefix + "username");
            String password = c.getProperty(prefix + "password");
            if (!db.authenticate(username, password.toCharArray())) {
                throw new RuntimeException("MongoDB authentication failed: "
                        + dbName);
            }
        }
        
        dbs_.put(dsKey, db);
        
        Morphia morphia = new Morphia();
        morphias_.put(dsKey, morphia);
        
        Datastore ds = morphia.createDatastore(mongo, dbName);
        ds.ensureIndexes();
        
        String writeConcern = Play.configuration.getProperty("morphia.defaultWriteConcern", "safe");
        if (null != writeConcern) {
            ds.setDefaultWriteConcern(WriteConcern.valueOf(writeConcern.toUpperCase()));
        }
        
        dataStores_.put(dsKey, ds);
        Logger.debug("Datasource %s initialized", dsKey);
        
    }

    private void initIdType_() {
        Properties c = Play.configuration;
        if (c.containsKey("morphia.id.type")) {
            debug("reading id type...");
            String s = c.getProperty("morphia.id.type");
            try {
                idType_ = IdType.valueOf(s);
                debug("ID Type set to : %1$s", idType_.name());
                if ("1.2beta".equals(VERSION) && idType_ == IdType.Long) {
                    warn("Caution: Using reference in your model entities might cause problem when you ID type set to Long. Check http://groups.google.com/group/morphia/browse_thread/thread/bdd51121c2845973");
                }
            } catch (Exception e) {
                String msg = msg_("Error configure morphia id type: %1$s. Id type set to default: ObjectId.", s);
                fatal(e, msg);
                throw new ConfigurationException(msg);
            }
        }
    }
    
    private final Pattern gridFSKeyPattern = 
            Pattern.compile(PREFIX + "([a-zA-Z0-9]+).collection.upload");

    //GridFS is special, it must be be configured on only one node 
    //in a multi-node configuration due to have the Blob class in implemented
    //If this is not a multi-node setup, then it can use a default 
    //setup with the default ds.
    private void setupGridFS() {
        Properties c = Play.configuration;
        Enumeration<String> propertyNames = (Enumeration<String>)c.propertyNames();
        
        String gridFsKeyName = null;
        String gridFsNodeName = null;
        
        while(propertyNames.hasMoreElements()) {
            String propertyName =  propertyNames.nextElement();
            
            Matcher matcher = gridFSKeyPattern.matcher(propertyName);
            if (matcher.matches()) {
                gridFsKeyName = matcher.group(0);
                gridFsNodeName = matcher.group(1);
            }
        }
        
        String dbName = null;
        String gridFsUploadDir = null;
        
        if (null != gridFsKeyName && !"".equals(gridFsKeyName)) {
            gridFsUploadDir = c.getProperty(gridFsKeyName, "upload");
            dbName = gridFsNodeName;
            
        } else {
            gridFsUploadDir = c.getProperty(PREFIX + "collection.upload", "upload");
            dbName = DEFAULT_DS_NAME;
        }
       
        
        DB db = dbs_.get(dbName);
        if (null == db) {
            throw new ConfigurationException("Datasource name not configured: " + dbName);
        }
        
        gridfs_ = new GridFS(db, gridFsUploadDir);
        Logger.debug("Initialized GridFS for db %s using path %s", db.getName(), gridFsUploadDir);
        
    }
    
    @Override
    public void onApplicationStart() {
        //configured_ = false;
        //onConfigurationRead();
        
        for (Morphia morphia : morphias_.values()) {
            if (morphia == null) throw new ConfigurationException("Morphia instance is somehow null");
            configureDs_(morphia);
        }
        
        Logger.info(msg_("loaded"));
    }

    @Override
    public void onInvocationException(Throwable e) {
        if (e instanceof MongoException.Network) {
            // try restart morphia plugin
            error("MongoException.Network encountered. Trying to restart mongo...");
            configured_ = false;
            onConfigurationRead();
        }
    }

    // @Override
    // public void detectChange() {
    // ds_.getMongo().close();
    // onConfigurationRead();
    // afterApplicationStart();
    // }

    private void configureDs_(Morphia morphia) {
        List<Class<?>> pending = new ArrayList<Class<?>>();
        Map<Class<?>, Integer> retries = new HashMap<Class<?>, Integer>();
        List<ApplicationClass> cs = Play.classes.all();
        for (ApplicationClass c : cs) {
            Class<?> clz = c.javaClass;
            if (clz.isAnnotationPresent(Entity.class)) {
                try {
                    debug("mapping class: %1$s", clz.getName());
                    morphia.map(clz);
                } catch (ConstraintViolationException e) {
                    error(e, "error mapping class [%1$s]", clz);
                    pending.add(clz);
                    retries.put(clz, 1);
                }
            }
        }

        while (!pending.isEmpty()) {
            for (Class<?> clz : pending) {
                try {
                    debug("mapping class: ", clz.getName());
                    morphia.map(clz);
                    pending.remove(clz);
                } catch (ConstraintViolationException e) {
                    error(e, "error mapping class [%1$s]", clz);
                    int retry = retries.get(clz);
                    if (retry > 2) {
                        throw new RuntimeException(
                                "too many errories mapping Morphia Entity classes");
                    }
                    retries.put(clz, retries.get(clz) + 1);
                }
            }
        }

        Logger.info(msg_("initialized %s", morphia.toString()));
        ds().ensureIndexes();

        String writeConcern = Play.configuration.getProperty(
                "morphia.defaultWriteConcern", "safe");
        if (null != writeConcern) {
            ds().setDefaultWriteConcern(
                    WriteConcern.valueOf(writeConcern.toUpperCase()));
        }
        info("initialized");
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object bind(String name, @SuppressWarnings("rawtypes") Class clazz,
            java.lang.reflect.Type type, Annotation[] annotations,
            Map<String, String[]> params) {
        if (Model.class.isAssignableFrom(clazz)) {
            String keyName = modelFactory(clazz).keyName();
            String idKey = name + "." + keyName;
            if (params.containsKey(idKey) && params.get(idKey).length > 0
                    && params.get(idKey)[0] != null
                    && params.get(idKey)[0].trim().length() > 0) {
                String id = params.get(idKey)[0];
                try {
                    
                    Datastore ds = ds(getDatasourceNameFromAnnotation(clazz));
                    Object o = ds.createQuery(clazz)
                            .filter(keyName, new ObjectId(id)).get();
                    return Model.edit(o, name, params, annotations);
                } catch (Exception e) {
                    return null;
                }
            }
            return Model.create(clazz, name, params, annotations);
        }
        return super.bind(name, clazz, type, annotations, params);
    }

    //TODO add a cache or figure out a bytecode enhancement
    public static String getDatasourceNameFromAnnotation(Class clazz) {
        if (Model.class.isAssignableFrom(clazz)) {
            for (Annotation annotation : clazz.getAnnotations()) {
                if (Datasource.class.isAssignableFrom(annotation.annotationType())) {
                    Datasource ds = (Datasource)annotation;
                    return ds.name();
                }
            }
        }
        return DEFAULT_DS_NAME;
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
        if (Model.class.isAssignableFrom(modelClass)
                && modelClass.isAnnotationPresent(Entity.class)) {
            return MorphiaModelLoader
                    .getFactory((Class<? extends Model>) modelClass);
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
                if (null == f)
                    f = new MorphiaModelLoader(clazz);
                return f;
            }
        }

        @Override
        public Model findById(Object id) {
            if (id == null)
                return null;
            try {
                Datastore ds = ds(getDatasourceNameFromAnnotation(clazz));
                return ds.find(clazz, keyName(),
                        Binder.directBind(id.toString(), keyType())).get();
            } catch (Exception e) {
                // Key is invalid, thus nothing was found
                warn(e, "cannot find entity[%s] with id: %s", clazz.getName(), id);
                return null;
            }
        }

        @Override
        public List<play.db.Model> fetch(int offset, int size, String orderBy,
                String order, List<String> searchFields, String keywords,
                String where) {
            if (orderBy == null)
                orderBy = keyName();
            if ("DESC".equalsIgnoreCase(order))
                orderBy = null == orderBy ? null : "-" + orderBy;
            Datastore ds = ds(getDatasourceNameFromAnnotation(clazz));
            Query<? extends Model> q = ds.createQuery(clazz).offset(offset)
                    .limit(size);
            if (null != orderBy)
                q = q.order(orderBy);

            if (keywords != null && !keywords.equals("")) {
                List<Criteria> cl = new ArrayList<Criteria>();
                for (String f : fillSearchFieldsIfEmpty_(searchFields)) {
                    cl.add(q.criteria(f).contains(keywords));
                }
                q.or(cl.toArray(new Criteria[] {}));
            }

            processWhere(q, where);

            List<play.db.Model> l = new ArrayList<play.db.Model>();
            l.addAll(q.asList());
            return l;
        }

        private List<String> fillSearchFieldsIfEmpty_(List<String> l) {
            if (l == null) {
                l = new ArrayList<String>();
            }
            if (l.isEmpty()) {
                listAllSearchableFields_(clazz, l, null);
                // for (Model.Property property : listProperties()) {
                // if (property.isSearchable) l.add(property.name);
                // }
            }
            return l;
        }

        @Override
        public Long count(List<String> searchFields, String keywords,
                String where) {
            Datastore ds = ds(getDatasourceNameFromAnnotation(clazz));
            Query<?> q = ds.createQuery(clazz);

            if (keywords != null && !keywords.equals("")) {
                List<Criteria> cl = new ArrayList<Criteria>();
                for (String f : fillSearchFieldsIfEmpty_(searchFields)) {
                    cl.add(q.criteria(f).contains(keywords));
                }
                q.or(cl.toArray(new Criteria[] {}));
            }

            processWhere(q, where);
            return q.countAll();
        }

        /*
         * Support the following syntax at the moment: property = 'val' property
         * in ('val1', 'val2' ...) prop1 ... and prop2 ...
         */
        public static void processWhere(Query<?> q, String where) {
            if (null != where) {
                where = where.trim();
            } else {
                where = "";
            }
            if ("".equals(where) || "null".equalsIgnoreCase(where))
                return;
            if (where.startsWith("function")) {
                q.where(where);
                return;
            }
            String[] propValPairs = where.split("(and|&&)");
            for (String propVal : propValPairs) {
                if (propVal.contains("=")) {
                    String[] sa = propVal.split("=");
                    if (sa.length != 2) {
                        throw new IllegalArgumentException(
                                "invalid where clause: " + where);
                    }
                    String prop = sa[0];
                    String val = sa[1];
                    debug("where prop val pair found: %1$s = %2$s", prop, val);
                    prop = prop.replaceAll("[\"' ]", "");
                    if (val.matches("[\"'].*[\"']")) {
                        // string value
                        val = val.replaceAll("[\"' ]", "");
                        q.filter(prop, val);
                    } else {
                        // possible string, number or boolean value
                        if (val.matches("[-+]?\\d+\\.\\d+")) {
                            q.filter(prop, Float.parseFloat(val));
                        } else if (val.matches("[-+]?\\d+")) {
                            q.filter(prop, Integer.parseInt(val));
                        } else if (val
                                .matches("(false|true|FALSE|TRUE|False|True)")) {
                            q.filter(prop, Boolean.parseBoolean(val));
                        } else {
                            q.filter(prop, val);
                        }
                    }
                } else if (propVal.contains(" in ")) {
                    String[] sa = propVal.split(" in ");
                    if (sa.length != 2) {
                        throw new IllegalArgumentException(
                                "invalid where clause: " + where);
                    }
                    String prop = sa[0].trim();
                    String val0 = sa[1].trim();
                    if (!val0.matches("\\(.*\\)")) {
                        throw new IllegalArgumentException(
                                "invalid where clause: " + where);
                    }
                    val0 = val0.replaceAll("[\\(\\)]", "");
                    String[] vals = val0.split(",");
                    List<Object> l = new ArrayList<Object>();
                    for (String val : vals) {
                        // possible string, number or boolean value
                        if (val.matches("[-+]?\\d+\\.\\d+")) {
                            l.add(Float.parseFloat(val));
                        } else if (val.matches("[-+]?\\d+")) {
                            l.add(Integer.parseInt(val));
                        } else if (val
                                .matches("(false|true|FALSE|TRUE|False|True)")) {
                            l.add(Boolean.parseBoolean(val));
                        } else {
                            l.add(val);
                        }
                    }
                    q.filter(prop + " in ", l);
                } else {
                    throw new IllegalArgumentException("invalid where clause: "
                            + where);
                }
            }
        }

        public void deleteAll() {
            Datastore ds = ds(getDatasourceNameFromAnnotation(clazz));
            ds.delete(ds.createQuery(clazz));
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
                if (f.isAnnotationPresent(Transient.class)
                        && !f.getType().equals(Blob.class)) {
                    continue;
                }
                Model.Property mp = buildProperty(f);
                if (mp != null) {
                    properties.add(mp);
                }
            }
            return properties;
        }

        // enumerable all searchable fields including embedded recursively
        private static void listAllSearchableFields_(Class<?> clazz,
                List<String> l, String prefix) {
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
                if (f.isAnnotationPresent(Embedded.class)) {
                    if (Collection.class.isAssignableFrom(f.getType())) {
                        final Class<?> fieldType = (Class<?>) ((ParameterizedType) f
                                .getGenericType()).getActualTypeArguments()[0];
                        if (fieldType.isAnnotationPresent(Embedded.class)) {
                            listAllSearchableFields_(fieldType, l,
                                    null == prefix ? f.getName() + "." : prefix
                                            + f.getName() + ".");
                        }
                    } else if (Map.class.isAssignableFrom(f.getType())) {
                        // TODO
                    } else {
                        listAllSearchableFields_(
                                f.getType(),
                                l,
                                null == prefix ? f.getName() + "." : prefix
                                        + f.getName() + ".");
                    }
                    continue;
                }

                if (f.getType().equals(String.class)) {
                    l.add(prefix == null ? f.getName() : prefix + f.getName());
                }
            }
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
                throw new UnexpectedException(
                        "Error while determining the object @Id for an object of type "
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
                            Datastore ds = ds(getDatasourceNameFromAnnotation(clazz));
                            return (List<Object>) ds.createQuery(
                                    field.getType()).asList();
                        }
                    };
                }
            }
            if (Collection.class.isAssignableFrom(field.getType())) {
                final Class<?> fieldType = (Class<?>) ((ParameterizedType) field
                        .getGenericType()).getActualTypeArguments()[0];
                if (field.isAnnotationPresent(Reference.class)) {
                    modelProperty.isRelation = true;
                    modelProperty.isMultiple = true;
                    modelProperty.relationType = fieldType;
                    modelProperty.choices = new Model.Choices() {
                        @SuppressWarnings("unchecked")
                        public List<Object> list() {
                            Datastore ds = ds(getDatasourceNameFromAnnotation(clazz));
                            return (List<Object>) ds.createQuery(fieldType)
                                    .asList();
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
