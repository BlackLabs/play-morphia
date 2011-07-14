package play.modules.morphia;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;
import play.Logger;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.classloading.enhancers.Enhancer;
import play.modules.morphia.Model.NoId;

import com.google.code.morphia.annotations.Embedded;
import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Id;
import com.google.code.morphia.annotations.PrePersist;

/**
 * This class uses the Play framework enhancement process to enhance classes
 * marked with the morphia annotations.
 * 
 * @author greenlaw110@gmail.com
 */
public class MorphiaEnhancer extends Enhancer {

    public static final String PACKAGE_NAME = "play.modules.morphia";

    @Override
    public void enhanceThisClass(ApplicationClass applicationClass) throws Exception {
        //Logger.debug("Morphia> start to enhance class:" + applicationClass.name);
        // We won't enhance class before we get ID_type information
        enhanceThisClass_(applicationClass);
    }
    
    void enhanceThisClass_(ApplicationClass applicationClass) throws Exception {
        // this method will be called after configuration finished
        // if (!MorphiaPlugin.configured()) return;

        final CtClass ctClass = makeClass(applicationClass);
        
        // Enhance MongoEntity annotated classes
        if (hasAnnotation(ctClass, Entity.class.getName()) && !hasAnnotation(ctClass, Model.ByPass.class.getName())) {
        	
            boolean addId = true;
            boolean embedded = false;
        	boolean autoTS = hasAnnotation(ctClass, Model.AutoTimestamp.class.getName());
            // if the class has Embedded annotation or any field has been annotated with @Id already
            if (hasAnnotation(ctClass, Embedded.class.getName())) {
                addId = false;
                embedded = true;
            } else if (hasAnnotation(ctClass, NoId.class.getName())) {
                addId = false;
            } else {
                for (CtField cf: ctClass.getDeclaredFields()) {
                    if (hasAnnotation(cf, Id.class.getName())) {
                        addId = false;
                        break;
                    }
                }
                for (CtField cf: ctClass.getFields()) {
                    if (hasAnnotation(cf, Id.class.getName())) {
                        addId = false;
                        break;
                    }
                }
            }
            
            enhance_(ctClass, applicationClass, addId, embedded, autoTS);
        } else {
            return;
        }
    }
    
    /**
     * Enhance classes marked with the MongoEntity annotation.
     * 
     * @param ctClass
     * @throws Exception
     */
    private void enhance_(CtClass ctClass, ApplicationClass applicationClass, boolean addId, boolean embedded, boolean autoTS) throws Exception {
        Logger.trace("Morphia: enhancing MorphiaEntity: " + ctClass.getName());

        // Don't need to fully qualify types when compiling methods below
        classPool.importPackage(PACKAGE_NAME);
        
        String entityName = ctClass.getName();
        String className = entityName + ".class";
        
        // ModalFactory
        // create an id field
        // CtField mf = new CtField(classPool.get(play.db.Model.Factory.class.getName()), "mf", ctClass);
        CtField mf = CtField.make("protected static play.db.Model.Factory mf = MorphiaPlugin.MorphiaModelLoader.getFactory(" + className + ");", ctClass);
        ctClass.addField(mf);
        // getModelFactory
        CtMethod getModelFactory = CtMethod.make("public static play.db.Model.Factory getModelFactory() { return mf; }",ctClass);
        ctClass.addMethod(getModelFactory);


        // id field
        if (addId) {
            Logger.trace("Adding id methods to system managed ID entity: %1$s", ctClass.getName());
            // create an id field
            Logger.trace("type name: %1$s", play.modules.morphia.utils.IdGenerator.getIdTypeName());
            CtField idField = new CtField(classPool.get(play.modules.morphia.utils.IdGenerator.getIdTypeName()), "_id", ctClass);
            idField.setModifiers(Modifier.PRIVATE);
            AnnotationsAttribute aa = new AnnotationsAttribute(ctClass.getClassFile().getConstPool(),
                    AnnotationsAttribute.visibleTag);
            Annotation idAnn = new Annotation(Id.class.getName(), ctClass.getClassFile().getConstPool());
            aa.addAnnotation(idAnn);
            idField.getFieldInfo().addAttribute(aa);
            ctClass.addField(idField);
            Logger.trace("ID field added to entity[%2$s]: %1$s", idField.getSignature(), ctClass.getName());
            // id()
            CtMethod getId = CtMethod.make("public Object getId() { return _id;}", ctClass);
            ctClass.addMethod(getId);
            // setId
            CtMethod setId = CtMethod.make("protected void setId_(Object id) { _id = (" + play.modules.morphia.utils.IdGenerator.getIdTypeName() + ")play.modules.morphia.utils.IdGenerator.processId(id);}", ctClass);
            ctClass.addMethod(setId);
        } else {
            if (!embedded) {
                Logger.trace("adding id methods to user defined id entity: %1$s", ctClass.getName());
                // a general id() method for user marked Id field
                boolean hasGetId = false;
                for (CtMethod cm: ctClass.getDeclaredMethods()) {
                    if ("getId".equals(cm.getName()) && cm.getDeclaringClass().equals(ctClass)) {
                        // user has defined getId already
                        hasGetId = true;
                        break;
                    }
                }
                if (!hasGetId) {
                    CtMethod getId = CtMethod.make("public Object getId() { return mf.keyValue(this);}", ctClass);
                    ctClass.addMethod(getId);
                }
                // setId - for user marked Id entity, setId method needs to be override 
                
                CtMethod isUserDefinedId = CtMethod.make("protected boolean isUserDefinedId_() {return super.isUserDefinedId_();}", ctClass);
                ctClass.addMethod(isUserDefinedId);
                
            } else {
                Logger.trace("adding id methods to embedded entity: %1$s", ctClass.getName());
                // -- embedded
                // getId() use Model default
                // CtMethod getId = CtMethod.make("public Object getId() { return null;}", ctClass);
                // ctClass.addMethod(getId);
                // setId
                CtMethod setId = CtMethod.make("protected void setId_(Object id) {throw new UnsupportedOperationException(\"embedded object does not support this method\");}", ctClass);
                ctClass.addMethod(setId);
                
                CtMethod isEmbedded = CtMethod.make("protected boolean isEmbedded_() {return true;}", ctClass);
                ctClass.addMethod(isEmbedded);
            }
        }
        
        // create timestamp?
        if (autoTS) {
        	Logger.trace("create timestamp fields automatically");
        	CtField createdField = new CtField(CtClass.longType, "_created", ctClass);
        	createdField.setModifiers(Modifier.PRIVATE);
        	ctClass.addField(createdField);
        	
        	CtField modifiedField = new CtField(CtClass.longType, "_modified", ctClass);
        	modifiedField.setModifiers(Modifier.PRIVATE);
        	ctClass.addField(modifiedField);
        	
        	CtMethod persistTs = CtMethod.make("void _updateTimestamp() { long now = System.currentTimeMillis(); if (0 == _created) {_created = now;} ;_modified = now;}", ctClass);
            AnnotationsAttribute aa = new AnnotationsAttribute(ctClass.getClassFile().getConstPool(),
                    AnnotationsAttribute.visibleTag);
            Annotation prePersistAnn = new Annotation(PrePersist.class.getName(), ctClass.getClassFile().getConstPool());
            aa.addAnnotation(prePersistAnn);
            persistTs.getMethodInfo().addAttribute(aa);
            ctClass.addMethod(persistTs);
        	
            CtMethod getCreated = CtMethod.make("public long _getCreated() { return _created; }", ctClass);
            ctClass.addMethod(getCreated);

            CtMethod getModified = CtMethod.make("public long _getModified() { return _modified; }", ctClass);
            ctClass.addMethod(getModified);
        }

        // all - alias of find()
        CtMethod all = CtMethod.make("public static play.modules.morphia.Model.MorphiaQuery all() { return new play.modules.morphia.Model.MorphiaQuery("+ className + "); }",ctClass);
        ctClass.addMethod(all);

        // create
        CtMethod create = CtMethod.make("public static play.modules.morphia.Model create(String name, play.mvc.Scope.Params params) { Object o = play.Play.classloader.loadClass(\""+ entityName + "\").newInstance(); return ((play.modules.morphia.Model)o).edit(name, params.all()); }",ctClass);
        ctClass.addMethod(create);

        // createQuery
        CtMethod createQuery = CtMethod.make("public static play.modules.morphia.Model.MorphiaQuery createQuery() { return all(); }",ctClass);
        ctClass.addMethod(createQuery);
        
        // disableValidation
        CtMethod disableValidation = CtMethod.make("public static play.modules.morphia.Model.MorphiaQuery disableValidation() { return all().disableValidation(); }",ctClass);
        ctClass.addMethod(disableValidation);

        // find -- alias: all()
        CtMethod find = CtMethod.make("public static play.modules.morphia.Model.MorphiaQuery find() { return all(); }",ctClass);
        ctClass.addMethod(find);
        
        // find(String keys, Object... params)
        CtMethod find2 = CtMethod.make("public static play.modules.morphia.Model.MorphiaQuery find(String keys, java.lang.Object[] params) { return createQuery().findBy(keys.substring(2), params); }",ctClass);
        ctClass.addMethod(find2);
        
        // findAll
        CtMethod findAll = CtMethod.make("public static java.util.List findAll() {return all().asList();}", ctClass);
        ctClass.addMethod(findAll);

        // filter(property, value)
        CtMethod filter = CtMethod.make("public static play.modules.morphia.Model.MorphiaQuery filter(String property, Object value) { return find().filter(property, value); }",ctClass);
        ctClass.addMethod(filter);

        // get()
        CtMethod get = CtMethod.make("public static Model get() { return find().get(); }",ctClass);
        ctClass.addMethod(get);

        // findById
        if (addId) {
            CtMethod findById = CtMethod.make("public static Model findById(java.lang.Object id) { return filter(\"_id\", play.modules.morphia.utils.IdGenerator.processId(id))._get(); }",ctClass);
            ctClass.addMethod(findById);
        } else {
            if (!embedded) {
                CtMethod findById = CtMethod.make("public static Model findById(java.lang.Object id) { return (Model)mf.findById(id); }",ctClass);
                ctClass.addMethod(findById);
            } else {
                // embedded class will throw out UnsupportedOperationException
            }
        }

        // count
        CtMethod count = CtMethod.make("public static long count() { return ds().getCount(" + className + "); }", ctClass);
        ctClass.addMethod(count);

        // count (String keys, Object... params)
        CtMethod count2 = CtMethod.make("public static long count(String keys, Object[] params) { return find(keys, params).count(); }", ctClass);
        ctClass.addMethod(count2);

        // deleteAll
        CtMethod deleteAll = CtMethod.make("public static long deleteAll() { return delete(all()); }",ctClass);
        ctClass.addMethod(deleteAll);

        // Done.
        applicationClass.enhancedByteCode = ctClass.toBytecode();
        ctClass.detach();
    }
}
