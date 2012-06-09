package play.test;

import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.scanner.ScannerException;
import play.Play;
import play.classloading.ApplicationClasses;
import play.data.binding.Binder;
import play.data.binding.ParamNode;
import play.data.binding.RootParamNode;
import play.db.jpa.JPAPlugin;
import play.exceptions.YAMLException;
import play.modules.morphia.Model;
import play.modules.morphia.MorphiaPlugin;

import com.google.code.morphia.Datastore;
import play.templates.TemplateLoader;
import play.vfs.VirtualFile;

public class MorphiaFixtures extends Fixtures {

	private static Datastore ds() {
        return MorphiaPlugin.ds();
    }

    public static void deleteDatabase() {
    	idCache.clear();
        Datastore ds = ds();
        for (Class<Model> clz: Play.classloader.getAssignableClasses(Model.class)) {
            ds.getCollection(clz).drop();
        }
    }

    public static void delete(Class<? extends Model> ... types) {
    	idCache.clear();
        for (Class<? extends Model> type: types) {
            ds().getCollection(type).drop();
        }
    }

    public static void delete(List<Class<? extends Model>> classes) {
    	idCache.clear();
        for (Class<? extends Model> type: classes) {
            ds().getCollection(type).drop();
        }
    }

    @SuppressWarnings("unchecked")
    public static void deleteAllModels() {
        List<Class<? extends Model>> mongoClasses = new ArrayList<Class<? extends Model>>();
        for (ApplicationClasses.ApplicationClass c : Play.classes.getAssignableClasses(play.db.Model.class)) {
        	Class<?> jc = c.javaClass;
        	mongoClasses.add((Class<? extends Model>) jc);
        }
        MorphiaFixtures.delete(mongoClasses);
    }

    /**
     * Load Model instances from a YAML file and persist them using the underlying persistence mechanism.
     * The format of the YAML file is constrained, see the Fixtures manual page
     * @param name Name of a YAML file somewhere in the classpath (or conf/)
     */
    public static void loadModels(String name) {
        VirtualFile yamlFile = null;
        try {
            for (VirtualFile vf : Play.javaPath) {
                yamlFile = vf.child(name);
                if (yamlFile != null && yamlFile.exists()) {
                    break;
                }
            }
            if (yamlFile == null) {
                throw new RuntimeException("Cannot load fixture " + name + ", the file was not found");
            }

            String renderedYaml = TemplateLoader.load(yamlFile).render();

            Yaml yaml = new Yaml();
            Object o = yaml.load(renderedYaml);
            if (o instanceof LinkedHashMap<?, ?>) {
                @SuppressWarnings("unchecked") LinkedHashMap<Object, Map<?, ?>> objects = (LinkedHashMap<Object, Map<?, ?>>) o;
                for (Object key : objects.keySet()) {
                    Matcher matcher = keyPattern.matcher(key.toString().trim());
                    if (matcher.matches()) {
                        // Type of the object. i.e. models.employee
                        String type = matcher.group(1);
                        // Id of the entity i.e. nicolas
                        String id = matcher.group(2);
                        if (!type.startsWith("models.")) {
                            type = "models." + type;
                        }

                        // Was the entity already defined?
                        if (idCache.containsKey(type + "-" + id)) {
                            throw new RuntimeException("Cannot load fixture " + name + ", duplicate id '" + id + "' for type " + type);
                        }


                        // Those are the properties that were parsed from the YML file
                        final Map<?, ?> entityValues =  objects.get(key);

                        // Prefix is object, why is that?
                        final Map<String, String[]> fields = serialize(entityValues, "object");


                        @SuppressWarnings("unchecked")
                        Class<play.db.Model> cType = (Class<play.db.Model>)Play.classloader.loadClass(type);
                        final Map<String, String[]> resolvedFields = resolveDependencies(cType, fields);

                        RootParamNode rootParamNode = ParamNode.convert(resolvedFields);
                        // This is kind of hacky. This basically says that if we have an embedded class we should ignore it.
                        if (play.db.Model.class.isAssignableFrom(cType)) {

                            play.db.Model model = (play.db.Model) Binder.bind(rootParamNode, "object", cType, cType, null);
                            for(Field f : model.getClass().getFields()) {
                                if (f.getType().isAssignableFrom(Map.class)) {
                                    f.set(model, objects.get(key).get(f.getName()));
                                }
                                if (f.getType().equals(byte[].class)) {
                                    f.set(model, objects.get(key).get(f.getName()));
                                }
                            }
                            model._save();

                            Class<?> tType = cType;
                            while (!tType.equals(Object.class)) {
                                idCache.put(tType.getName() + "-" + id, play.db.Model.Manager.factoryFor(cType).keyValue((play.db.Model)model));
                                tType = tType.getSuperclass();
                            }
                        }
                        else {
                            idCache.put(cType.getName() + "-" + id, Binder.bind(rootParamNode, "object", cType, cType, null));
                        }
                    }
                }
            }
            // Most persistence engine will need to clear their state
            Play.pluginCollection.afterFixtureLoad();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Class " + e.getMessage() + " was not found", e);
        } catch (ScannerException e) {
            throw new YAMLException(e, yamlFile);
        } catch (Throwable e) {
            e.printStackTrace();
            throw new RuntimeException("Cannot load fixture " + name + ": " + e.getMessage(), e);
        }
    }

    static Map<String, String[]> resolveDependencies(Class<play.db.Model> type, Map<String, String[]> yml) {

        // Contains all the fields (object properties) we should look up
        final Set<Field> fields = new HashSet<Field>();
        final Map<String, String[]> resolvedYml = new HashMap<String, String[]>();
        resolvedYml.putAll(yml);

        // Look up the super classes
        Class<?> clazz = type;
        while (!clazz.equals(Object.class)) {
            Collections.addAll(fields, clazz.getDeclaredFields());
            clazz = clazz.getSuperclass();
        }


        // Iterate through the Entity property list
        // @Embedded are not managed by the JPA plugin
        // This is not the nicest way of doing things.
        //modelFields =  Model.Manager.factoryFor(type).listProperties();
        final List<play.db.Model.Property> modelFields =  Model.Manager.factoryFor(type).listProperties();

        for (play.db.Model.Property field : modelFields) {
            // If we have a relation, get the matching object
            if (field.isRelation) {
                // These are the Ids that were set in the yml file (i.e person(nicolas)-> nicolas is the id)
                final String[] ids = resolvedYml.get("object." + field.name);
                if (ids != null) {
                    final String[] resolvedIds = new String[ids.length];
                    for (int i = 0; i < ids.length; i++) {
                        final String id = field.relationType.getName() + "-" + ids[i];
                        if (!idCache.containsKey(id)) {
                            throw new RuntimeException("No previous reference found for object of type " + field.name + " with key " + ids[i]);
                        }
                        // We now get the primary key
                        resolvedIds[i] = idCache.get(id).toString();
                    }
                    // Set the primary keys instead of the object itself.
                    // Model.Manager.factoryFor((Class<? extends Model>)field.relationType).keyName() returns the primary key label.
                    if (play.db.Model.class.isAssignableFrom(field.relationType )) {
                        resolvedYml.put("object." + field.name + "." + play.db.Model.Manager.factoryFor((Class<? extends play.db.Model>)field.relationType).keyName(), resolvedIds);
                    } else {
                        // Might be an embedded object
                        final String id = field.relationType.getName() + "-" + ids[0];
                        Object o = idCache.get(id);
                        // This can be a composite key
                        if (o.getClass().isArray()) {
                            for (Object a : (Object[])o) {
                                for (Field f : field.relationType.getDeclaredFields()) {
                                    try {
                                        resolvedYml.put("object." + field.name + "." + f.getName(), new String[] {f.get(a).toString()});
                                    } catch(Exception e) {
                                        // Ignores
                                    }
                                }
                            }
                        } else {
                            for (Field f : field.relationType.getDeclaredFields()) {
                                try {
                                    resolvedYml.put("object." + field.name + "." + f.getName(), new String[] {f.get(o).toString()});
                                } catch(Exception e) {
                                    // Ignores
                                }
                            }
                        }
                    }
                }

                resolvedYml.remove("object." + field.name);
            }
        }
        // Returns the map containing the ids to load for this object's relation.
        return resolvedYml;
    }

}
