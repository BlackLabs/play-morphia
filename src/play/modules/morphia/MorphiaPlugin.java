package play.modules.morphia;

import com.mongodb.*;
import com.mongodb.gridfs.GridFS;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.bson.types.ObjectId;
import org.mongodb.morphia.AbstractEntityInterceptor;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.annotations.*;
import org.mongodb.morphia.logging.LoggerFactory;
import org.mongodb.morphia.logging.MorphiaLoggerFactory;
import org.mongodb.morphia.mapping.Mapper;
import org.mongodb.morphia.mapping.validation.ConstraintViolationException;
import org.mongodb.morphia.query.Criteria;
import org.mongodb.morphia.query.Query;
import org.osgl._;
import org.osgl.storage.IStorageService;
import org.osgl.storage.KeyGenerator;
import org.osgl.util.C;
import org.osgl.util.E;
import org.osgl.util.S;
import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.data.binding.Binder;
import play.db.Model.Factory;
import play.exceptions.ConfigurationException;
import play.exceptions.UnexpectedException;
import play.modules.morphia.Model.MorphiaQuery;
import play.modules.morphia.MorphiaEvent.IMorphiaEventHandler;
import play.modules.morphia.utils.PlayLoggerFactory;
import play.modules.morphia.utils.SilentLoggerFactory;

/**
 * The plugin for the Morphia module.
 *
 * @author greenlaw110@gmail.com
 */
public final class MorphiaPlugin extends PlayPlugin {

    public static final String VERSION = "1.5.1";

    public static void info(String msg, Object... args) {
        Logger.info(msg_(msg, args));
    }

    public static void info(Throwable t, String msg, Object... args) {
        Logger.info(t, msg_(msg, args));
    }

    public static void debug(String msg, Object... args) {
        Logger.debug(msg_(msg, args));
    }

    public static void debug(Throwable t, String msg, Object... args) {
        Logger.debug(t, msg_(msg, args));
    }

    public static void trace(String msg, Object... args) {
        Logger.trace(msg_(msg, args));
    }

    public static void trace(Throwable t, String msg, Object... args) {
        Logger.warn(t, msg_(msg, args));
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

    private final MorphiaEnhancer e_ = new MorphiaEnhancer();
    
    private static MongoCredential credential_ = null;

    private static Morphia morphia_ = null;
    private static Datastore ds_ = null;
    private static GridFS gridfs;

    public static boolean migrateData = false;

    private static boolean configured_ = false;

    private static boolean appStarted_ = false;

    private static boolean loggerRegistered_ = false;

    static boolean autoTS_ = true;

    public static boolean loggerRegistered() {
        return loggerRegistered_;
    }

    static boolean postPluginEvent = false;

    public static boolean configured() {
        return configured_;
    }

    public static enum IdType {
        STRING, LONG, OBJECT_ID;

        public static IdType parseStr(String s) {
            if ("Long".equalsIgnoreCase(s)) return LONG;
            if ("String".equalsIgnoreCase(s)) return STRING;

            return OBJECT_ID;
        }

        public boolean isObjectId() {
            return this == OBJECT_ID;
        }
    }

    public static enum StringIdGenerator {
        OBJECT_ID() {
            @Override
            public String generate() {
                return new ObjectId().toString();
            }
        }, UUID() {
            @Override
            public String generate() {
                return java.util.UUID.randomUUID().toString();
            }
        };

        public abstract String generate();
    }

    private static IdType idType_ = null;

    public static IdType getIdType() {
        if (null == idType_) {
            initIdType_();
        }
        return idType_;
    }

    private static StringIdGenerator stringIdGenerator_ = null;

    public static String generateStringId() {
        if (null == stringIdGenerator_) {
            initStringIdGenerator_();
        }
        return stringIdGenerator_.generate();
    }

    public static Datastore ds() {
        return ds_;
    }

    public static GridFS gridFs() {
        return gridfs;
    }

    private final static ConcurrentMap<String, Datastore> dataStores_ = new ConcurrentHashMap<String, Datastore>();

    public static Datastore ds(String dbName) {
        if (S.empty(dbName))
            return ds();
        Datastore ds = dataStores_.get(dbName);
        if (null == ds) {
            Datastore ds0 = morphia_.createDatastore(mongo_, dbName);
            ds = dataStores_.putIfAbsent(dbName, ds0);
            if (null == ds) {
                ds = ds0;
            }
        }
        return ds;
    }

    public static Morphia morphia() {
        return morphia_;
    }

    @Override
    public void enhance(ApplicationClass applicationClass) throws Exception {
        //onConfigurationRead(); // ensure configuration be read before
        // enhancement
        initIdType_();
        initCrud_();
        e_.enhanceThisClass(applicationClass);
    }

    private static List<IMorphiaEventHandler> globalEventHandlers_ = new ArrayList<IMorphiaEventHandler>();
    private static Map<Class<? extends Model>, List<IMorphiaEventHandler>> modelEventHandlers_ = new HashMap<Class<? extends Model>, List<IMorphiaEventHandler>>();

    public static synchronized void registerGlobalEventHandler(IMorphiaEventHandler handler) {
        if (null == handler) throw new NullPointerException();
        if (!globalEventHandlers_.contains(handler)) globalEventHandlers_.add(handler);
    }

    public static synchronized void unregisterGlobalEventHandler(IMorphiaEventHandler handler) {
        if (null == handler) throw new NullPointerException();
        globalEventHandlers_.remove(handler);
    }

    public static synchronized void clearGlobalEventHandler() {
        globalEventHandlers_.clear();
    }

    public static synchronized void registerModelEventHandler(Class<? extends Model> model, IMorphiaEventHandler handler) {
        if (null == handler || null == model) throw new NullPointerException();
        List<IMorphiaEventHandler> l = modelEventHandlers_.get(model);
        if (null == l) {
            l = new ArrayList<IMorphiaEventHandler>();
            modelEventHandlers_.put(model, l);
        }
        if (!l.contains(l)) {
            l.add(handler);
        }
    }

    public static synchronized void unregisterModelEventHandler(Class<? extends Model> model, IMorphiaEventHandler handler) {
        if (null == handler || null == model) throw new NullPointerException();
        List<IMorphiaEventHandler> l = modelEventHandlers_.get(model);
        if (null == l) {
            return;
        }
        l.remove(handler);
    }

    public static synchronized void clearModelEventHandler(Class<? extends Model> model) {
        if (null == model) throw new NullPointerException();
        List<IMorphiaEventHandler> l = modelEventHandlers_.get(model);
        if (null != l) {
            l.clear();
            modelEventHandlers_.remove(model);
        }
    }

    public static synchronized void clearAllModelEventHandler() {
        modelEventHandlers_.clear();
    }

    static void onLifeCycleEvent(MorphiaEvent event, Model model) {
        Class<? extends Model> c = model.getClass();
        List<IMorphiaEventHandler> l = modelEventHandlers_.get(c);
        if (null != l) {
            for (IMorphiaEventHandler h : l) {
                event.invokeOn(h, model);
            }
        }

        for (IMorphiaEventHandler h : globalEventHandlers_) {
            event.invokeOn(h, model);
        }
    }

    static void onBatchLifeCycleEvent(MorphiaEvent event, MorphiaQuery query) {
        Class<? extends Model> c = query.getEntityClass();
        List<IMorphiaEventHandler> l = modelEventHandlers_.get(c);
        if (null != l) {
            for (IMorphiaEventHandler h : l) {
                event.invokeOn(h, query);
            }
        }

        for (IMorphiaEventHandler h : globalEventHandlers_) {
            event.invokeOn(h, query);
        }
    }

    private static MongoClient mongo_;

    /*
     * Connect using conf - morphia.db.host=host1,host2... -
     * morphia.db.port=port1,port2...
     */
    private final MongoClient connect_(String host, String port, MongoClientOptions options, MongoCredential creds) {
        String[] ha = host.split("[,\\s;]+");
        String[] pa = port.split("[,\\s;]+");
        int len = ha.length;
        if (len != pa.length)
           throw new ConfigurationException("host and ports number does not match");
        if (1 == len) {
           try {
              return new MongoClient(new ServerAddress(ha[0], Integer.parseInt(pa[0])), options);
           } catch (Exception e) {
              throw new ConfigurationException(String.format("Cannot connect to mongodb at %s:%s", host, port));
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
           throw new ConfigurationException("Cannot connect to mongodb: no replica can be connected");
        }
        if (creds != null)
           return new MongoClient(addrs, Arrays.asList(creds), options);
        else
           return new MongoClient(addrs, options);
     }

    /*
     * Connect using conf morphia.db.seeds=host1[:port1];host2[:port2]...
     */
    private final MongoClient connect_(String seeds, MongoClientOptions options, MongoCredential creds) {
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
           } catch (Exception e) {
              error(e, "error creating mongo connection to %s:%s", host, port);
           }
        }
        if (addrs.isEmpty()) {
           throw new ConfigurationException("Cannot connect to mongodb: no replica can be connected");
        }
        if (creds != null)
           return new MongoClient(addrs, Arrays.asList(creds), options);
        else
           return new MongoClient(addrs, options);
     }

    /*
     * Connect using conf morphia.db.url=mongodb://fred:foobar@host:port/db
     */
    private final MongoClient connect_(MongoClientURI mongoURI) {
        try {
           return new MongoClient(mongoURI);
        } catch (Exception e) {
           throw new ConfigurationException("Error creating mongo connection to " + mongoURI);
        }
     }

    public static BlobStorageService bss(KeyGenerator keygen, String ss) {
        return BlobStorageService.valueOf(keygen, ss);
    }

    public static Map<String, Class<? extends IStorageService>> ssMap = C.newMap("gfs", GridFSStorageService.class);

    public static Map<String, Map<String, String>> ssConfs = C.newMap();
    public static String defaultStorage = "gfs";

    public static Class<? extends IStorageService> getStorageClass(String storage) {
        if (!configured_) {
            // Rythm is precompiling app code before morphia plugin configured, let's 
            // do it here
            new MorphiaPlugin().onConfigurationRead();
        }
        return ssMap.get(storage);
    }

    public static Map<String, String> getStorageConfig(String storage) {
        Map<String, String> m = ssConfs.get(storage);
        if (null == m) {
            return C.map();
        } else {
            return C.newMap(m);
        }
    }

    public static Class<? extends IStorageService> getDefaultStorageClass() {
        return ssMap.get(defaultStorage);
    }

    public static Map<String, String> getDefaultStorageConf() {
        return ssConfs.get(defaultStorage);
    }

    public static Boolean crud = null;

    private void initCrud_() {
        if (null != crud) {
            return;
        }
        if (!Boolean.parseBoolean(Play.configuration.getProperty("morphia.crud", "false"))) {
            crud = false;
            return;
        }
        try {
            //Class.forName("controllers.CRUD");
            crud = true;
        } catch (Exception e) {
            throw new ConfigurationException("Cannot find CRUD class. Please make sure CRUD module is enabled for your application; or disable 'morphia.crud' option");
        }
    }

    @Override
    public void onLoad() {
        initCrud_();
    }

    @Override
    public void onConfigurationRead() {
        if (configured_)
            return;
        debug("reading configuration");
        initIdType_();
        Properties conf = Play.configuration;
        MorphiaPlugin.postPluginEvent = Boolean.parseBoolean(conf.getProperty("morphia.postPluginEvent", "false"));
        configureConnection_();
        migrateData = Boolean.parseBoolean(conf.getProperty("morphia.storage.migrateData", "false"));
        defaultStorage = conf.getProperty("morphia.storage.default", "gfs");
        String storage = conf.getProperty("morphia.storage");
        if (S.notEmpty(storage)) {
            Set<String> storages = C.setOf(storage.split("[, ;:\t]+"));
            Map<String, String> ssConf = C.newMap();
            for (Object k : conf.keySet()) {
                if (S.string(k).startsWith("morphia.storage.")) {
                    ssConf.put(S.after(k.toString(), "morphia."), conf.get(k).toString());
                }
            }
            for (String s : storages) {
                String ssCls = conf.getProperty(S.fmt("morphia.storage.%s.serviceImpl", s));
                if (null == ssCls) {
                    E.invalidConfiguration("cannot find serviceImpl for morphia storage: %s", s);
                }
                Class<? extends IStorageService> cls = _.classForName(ssCls);
                ssMap.put(s, cls);
                ssConfs.put(s, ssConf);
            }
            if (!ssMap.keySet().contains(defaultStorage)) {
                E.invalidConfiguration("default storage[%s] implementation not found", defaultStorage);
            }
        }
        configured_ = true;
    }

    private void configureConnection_() {
        Properties c = Play.configuration;
        MongoClientOptions options = readMongoOptions(c);

        String url = c.getProperty(PREFIX + "url");
        String seeds = c.getProperty(PREFIX + "seeds");
        String dbName = c.getProperty(PREFIX + "name");
        String username = c.getProperty(PREFIX + "username");
        String password = c.getProperty(PREFIX + "password");

        if (!S.empty(username) && !S.empty(password)) {
           credential_ = MongoCredential.createCredential(username, dbName, password.toCharArray());
        }

        if (!S.empty(url)) {
           MongoClientURI mongoURI = new MongoClientURI(url);
           mongo_ = connect_(mongoURI);
        } else if (!S.empty(seeds)) {
           mongo_ = connect_(seeds, options, credential_);
        } else {
           String host = c.getProperty(PREFIX + "host", "localhost");
           String port = c.getProperty(PREFIX + "port", "27017");
           mongo_ = connect_(host, port, options, credential_);
        }
     }

    private static MongoClientOptions readMongoOptions(Properties c) {
        MongoClientOptions.Builder builder = MongoClientOptions.builder();
        for (Method method : MongoClientOptions.Builder.class.getMethods()) {
           String property = c.getProperty("morphia.driver." + method.getName());
           if (StringUtils.isEmpty(property))
              continue;

           Class<?> fieldType = method.getParameterTypes()[0];
           Object value = null;
           try {
              if (fieldType == int.class)
                 method.invoke(builder, Integer.parseInt(property));
              else if (fieldType == long.class)
                 method.invoke(builder, Long.parseLong(property));
              else if (fieldType == String.class)
                 method.invoke(builder, property);
              else if (fieldType == Double.class)
                 method.invoke(builder, Double.parseDouble(property));
              else if (fieldType == boolean.class)
                 method.invoke(builder, Boolean.parseBoolean(property));
           } catch (Exception e) {
              error(e, "error setting mongo option " + method.getName());
           }
        }
        return builder.build();
     }

    @SuppressWarnings("unchecked")
    private void initMorphia_() {
        Properties c = Play.configuration;

        String url = c.getProperty(PREFIX + "url");
        String dbName = c.getProperty(PREFIX + "name");
        String username = c.getProperty(PREFIX + "username");
        String password = c.getProperty(PREFIX + "password");

        if (!S.empty(url)) {
           MongoClientURI mongoURI = new MongoClientURI(url);
           dbName = mongoURI.getDatabase();
           // overwrite these if set via url
           if (mongoURI.getUsername() != null) {
              username = mongoURI.getUsername();
           }
           if (mongoURI.getPassword() != null) {
              password = new String(mongoURI.getPassword());
           }
        }

        if (null == dbName) {
           warn("mongodb name not configured! using [test] db");
           dbName = "test";
        }

        String loggerClass = c.getProperty("morphia.logger");
        Class<? extends LoggerFactory> loggerClazz = SilentLoggerFactory.class;
        if (null != loggerClass) {
           final Pattern P_PLAY = Pattern.compile("(play|enable|true|yes|on)", Pattern.CASE_INSENSITIVE);
           final Pattern P_SILENT = Pattern.compile("(silent|disable|false|no|off)", Pattern.CASE_INSENSITIVE);
           if (P_PLAY.matcher(loggerClass).matches()) {
              loggerClazz = PlayLoggerFactory.class;
           } else if (!P_SILENT.matcher(loggerClass).matches()) {
              try {
                 loggerClazz = (Class<? extends LoggerFactory>) Class.forName(loggerClass);
              } catch (Exception e) {
                 warn("Cannot init morphia logger factory using %s. Use PlayLoggerFactory instead", loggerClass);
              }
           }
        }
        loggerRegistered_ = false;
        MorphiaLoggerFactory.reset();
        MorphiaLoggerFactory.registerLogger(loggerClazz);
        morphia_ = new Morphia();
        loggerRegistered_ = true;
        ds_ = morphia_.createDatastore(mongo_, dbName);
        dataStores_.put(dbName, ds_);

        String uploadCollection = c.getProperty("morphia.collection.upload", "uploads");
        gridfs = new GridFS(MorphiaPlugin.ds().getDB(), uploadCollection);

        morphia_.getMapper().addInterceptor(new AbstractEntityInterceptor() {
           @Override
           public void postLoad(Object ent, DBObject dbObj, Mapper mapr) {
              if (ent instanceof Model) {
                 Model m = (Model) ent;
                 PlayPlugin.postEvent(MorphiaEvent.LOADED.getId(), ent);
                 m._h_Loaded();
              }
           }

           @Override
           public void preLoad(Object ent, DBObject dbObj, Mapper mapr) {
              if (ent instanceof Model) {
                 PlayPlugin.postEvent(MorphiaEvent.ON_LOAD.getId(), ent);
                 ((Model) ent)._h_OnLoad();
              }
           }
        });
     }

    private static void initStringIdGenerator_() {
        if (null != stringIdGenerator_) {
            return;
        }
        Properties c = Play.configuration;
        String s = c.getProperty("morphia.id.stringIdGenerator", StringIdGenerator.OBJECT_ID.name());
        if (s.equalsIgnoreCase(StringIdGenerator.UUID.name())) {
            stringIdGenerator_ = StringIdGenerator.UUID;
        } else {
            stringIdGenerator_ = StringIdGenerator.OBJECT_ID;
        }
    }

    private static void initIdType_() {
        if (null != idType_) return;
        Properties c = Play.configuration;
        if (c.containsKey("morphia.id.type")) {
            debug("reading id type...");
            String s = c.getProperty("morphia.id.type");
            try {
                idType_ = IdType.parseStr(s);
                debug("ID Type set to : %1$s", idType_.name());
                if (idType_ == IdType.LONG && "1.2beta".equals(VERSION)) {
                    warn("Caution: Using reference in your model entities might cause problem when you ID type set to LONG. Check http://groups.google.com/group/morphia/browse_thread/thread/bdd51121c2845973");
                }
            } catch (Exception e) {
                String msg = msg_("Error configure morphia id type: %1$s. Id type set to default: OBJECT_ID.", s);
                fatal(e, msg);
                throw new ConfigurationException(msg);
            }
        } else {
            idType_ = IdType.OBJECT_ID;
        }
    }

    private static void initAutoTS_() {
        String s = Play.configuration.getProperty("morphia.autoTimestamp", "true");
        autoTS_ = Boolean.parseBoolean(s);
    }

    private static void initSeq_() {
        Seq.initSelf();
    }

    // -- used to map field name to mongo db column name
    public static Map<Class, Map<String, String>> colNameMap = new HashMap();

    private void initColNameMap() {
        long l = System.currentTimeMillis();
        boolean initSeq = idType_ == IdType.LONG;
        for (ApplicationClass ac : Play.classes.getAnnotatedClasses(Entity.class)) {
            Class<?> c = ac.javaClass;
            if (Modifier.isAbstract(c.getModifiers())) continue;

            if (initSeq) {
                Seq.init(c);
            }

            List<Field> fields = new ArrayList<Field>();
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
            fields.addAll(Arrays.asList(c.getFields()));
            for (Field f : fields) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                Property p = f.getAnnotation(Property.class);
                if (null != p) {
                    Map<String, String> m = colNameMap.get(c);
                    if (null == m) {
                        m = new HashMap<String, String>();
                        colNameMap.put(c, m);
                    }
                    m.put(f.getName(), p.value());
                } else {
                    Model.Column col = f.getAnnotation(Model.Column.class);
                    if (null != col) {
                        Map<String, String> m = colNameMap.get(c);
                        if (null == m) {
                            m = new HashMap<String, String>();
                            colNameMap.put(c, m);
                        }
                        m.put(f.getName(), col.value());
                    }
                }
            }
        }
        if (Logger.isTraceEnabled()) {
            trace("%sms to init column name map", System.currentTimeMillis() - l);
        }
    }

    public static String mongoColName(Class c, String fieldName) {
        Map<String, String> m = colNameMap.get(c);
        if (null == m) return fieldName;
        String s = m.get(fieldName);
        return null == s ? fieldName : s;
    }

    @Override
    public void onApplicationStart() {
        if (!appStarted_) {
            // reload all at dev mode
            configureConnection_();
        }
        initMorphia_();
        configureDs_();
        registerEventHandlers_();
        initColNameMap();
        initAutoTS_();
        if (idType_ == IdType.LONG) {
            initSeq_();
        }
        info("initialized");
        appStarted_ = true;
    }

    @Override
    public void onApplicationStop() {
        mongo_.close();
        appStarted_ = false;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void registerEventHandlers_() {
        if (!Boolean.parseBoolean(Play.configuration.getProperty("morphia.autoRegisterEventHandler", "true"))) return;

        // -- register handlers from event handler class --
        List<Class> classes = Play.classloader.getAssignableClasses(IMorphiaEventHandler.class);
        for (Class c : classes) {
            IMorphiaEventHandler h = null;
            try {
                Constructor cnst = c.getDeclaredConstructor();
                cnst.setAccessible(true);
                h = (IMorphiaEventHandler) cnst.newInstance();
            } catch (Exception e) {
                Logger.error(e, "Cannot init IMorphiaEventHandler from class: %s", c.getName());
                continue;
            }
            Watch w = (Watch) c.getAnnotation(Watch.class);
            if (null != w) {
                Class[] ca = w.value();
                for (Class modelClass : ca) {
                    registerModelEventHandlers_(modelClass, h);
                }
            }
        }

        // -- register handlers from model class --
        classes = Play.classloader.getAssignableClasses(Model.class);
        for (Class c : classes) {
            WatchBy wb = (WatchBy) c.getAnnotation(WatchBy.class);
            if (null == wb) continue;
            Class[] ca = wb.value();
            for (Class handler : ca) {
                if ((IMorphiaEventHandler.class.isAssignableFrom(handler))) {
                    IMorphiaEventHandler h = null;
                    try {
                        Constructor cnst = handler.getDeclaredConstructor();
                        cnst.setAccessible(true);
                        h = (IMorphiaEventHandler) cnst.newInstance();
                    } catch (Exception e) {
                        Logger.error(e, "Cannot init IMorphiaEventHandler from class: %s", c.getName());
                        continue;
                    }
                    registerModelEventHandlers_(c, h);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void registerModelEventHandlers_(@SuppressWarnings("rawtypes") Class modelClass, IMorphiaEventHandler h) {
        if (Model.class.equals(modelClass)) {
            registerGlobalEventHandler(h);
            return;
        }
        if (Model.class.isAssignableFrom(modelClass)) {
            if (!Modifier.isAbstract(modelClass.getModifiers())) registerModelEventHandler(modelClass, h);
            @SuppressWarnings("rawtypes")
            List<Class> lc = Play.classloader.getAssignableClasses(modelClass);
            lc.remove(modelClass);
            for (@SuppressWarnings("rawtypes") Class c : lc) {
                registerModelEventHandlers_(c, h);
            }
        }
    }

    @Override
    public void onInvocationException(Throwable e) {
        if (e instanceof MongoException) {
           error("MongoException.Network encountered. Trying to restart mongo...");
           configureConnection_();
           initMorphia_();
        }
     }

    private void configureDs_() {
//        List<Class<?>> pending = new ArrayList<Class<?>>();
//        Map<Class<?>, Integer> retries = new HashMap<Class<?>, Integer>();
        List<ApplicationClass> cs = Play.classes.all();
        for (ApplicationClass c : cs) {
            Class<?> clz = c.javaClass;
            if (clz.isAnnotationPresent(Entity.class)) {
                try {
                    debug("mapping class: %1$s", clz.getName());
                    morphia_.map(clz);
                } catch (ConstraintViolationException e) {
                    throw new RuntimeException(e);
//                    error(e, "error mapping class [%1$s]", clz);
//                    pending.add(clz);
//                    retries.put(clz, 1);
                }
            }
        }
//
//        while (!pending.isEmpty()) {
//            for (Class<?> clz : pending) {
//                try {
//                    debug("mapping class: ", clz.getName());
//                    morphia_.map(clz);
//                    pending.remove(clz);
//                } catch (ConstraintViolationException e) {
//                    error(e, "error mapping class [%1$s]", clz);
//                    int retry = retries.get(clz);
//                    if (retry > 2) {
//                        throw new RuntimeException(
//                                "too many errories mapping Morphia Entity classes");
//                    }
//                    retries.put(clz, retries.get(clz) + 1);
//                }
//            }
//        }

        mongo_.setWriteConcern(WriteConcern.UNACKNOWLEDGED);
        ds().ensureIndexes();

        String writeConcern = Play.configuration.getProperty(
                "morphia.defaultWriteConcern", "safe");
        if (null != writeConcern) {
            ds().setDefaultWriteConcern(
                    WriteConcern.valueOf(writeConcern.toUpperCase()));
        }
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
                    Object o = ds().createQuery(clazz)
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

        private final Class<? extends Model> clazz;

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
                return ds().find(clazz, keyName(), Binder.directBind(id.toString(), keyType())).get();
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
            Query<? extends Model> q = ds().createQuery(clazz).offset(offset)
                    .limit(size);
            if (null != orderBy)
                q = q.order(orderBy);

            if (keywords != null && !keywords.equals("")) {
                List<Criteria> cl = new ArrayList<Criteria>();
                String[] sa = keywords.split("[\\W]+");
                for (String f : fillSearchFieldsIfEmpty_(searchFields)) {
                    List<Criteria> cl0 = new ArrayList<Criteria>();
                    for (String s : sa) {
                        cl0.add(q.criteria(f).containsIgnoreCase(s));
                    }
                    cl.add(q.and(cl0.toArray(new Criteria[]{})));
                }
                q.or(cl.toArray(new Criteria[]{}));
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
            Query<?> q = ds().createQuery(clazz);

            if (keywords != null && !keywords.equals("")) {
                List<Criteria> cl = new ArrayList<Criteria>();
                String[] sa = keywords.split("[\\W]+");
                for (String f : fillSearchFieldsIfEmpty_(searchFields)) {
                    for (String s : sa) {
                        cl.add(q.criteria(f).containsIgnoreCase(keywords));
                    }
                }
                q.or(cl.toArray(new Criteria[]{}));
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
                    val = S.trim(val);
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

        @Override
        public void deleteAll() {
           ds().delete(ds().createQuery(clazz));
        }

        @Override
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
              if (f.isAnnotationPresent(Transient.class) && !f.getType().equals(Blob.class)) {
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

        @Override
        public String keyName() {
           Field f = keyField();
           return (f == null) ? null : f.getName();
        }

        @Override
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
                     @Override
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
                     @Override
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
                     @Override
                     @SuppressWarnings("unchecked")
                     public List<Object> list() {
                        return (List<Object>) ds().createQuery(fieldType).asList();
                     }
                  };
               }
            }
            if (field.getType().isEnum()) {
               modelProperty.choices = new Model.Choices() {

                  @Override
                  @SuppressWarnings("unchecked")
                  public List<Object> list() {
                     return (List<Object>) Arrays.asList(field.getType().getEnumConstants());
                  }
               };
            }
            modelProperty.name = field.getName();
            if (field.getType().equals(String.class)) {
               modelProperty.isSearchable = true;
            }
            return modelProperty;
         }

    }

}
