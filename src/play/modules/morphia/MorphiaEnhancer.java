package play.modules.morphia;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.EnumMemberValue;
import javassist.bytecode.annotation.MemberValue;
import play.Logger;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.classloading.enhancers.Enhancer;
import play.modules.morphia.Model.Added;
import play.modules.morphia.Model.BatchDeleted;
import play.modules.morphia.Model.ByPass;
import play.modules.morphia.Model.Column;
import play.modules.morphia.Model.Deleted;
import play.modules.morphia.Model.Loaded;
import play.modules.morphia.Model.MorphiaQuery;
import play.modules.morphia.Model.NoId;
import play.modules.morphia.Model.OnAdd;
import play.modules.morphia.Model.OnBatchDelete;
import play.modules.morphia.Model.OnDelete;
import play.modules.morphia.Model.OnLoad;
import play.modules.morphia.Model.OnUpdate;
import play.modules.morphia.Model.Updated;
import play.modules.morphia.utils.StringUtil;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Id;
import com.google.code.morphia.annotations.PrePersist;
import com.google.code.morphia.annotations.Property;
import com.google.code.morphia.annotations.Transient;
import com.google.code.morphia.utils.IndexDirection;

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
        // is anonymous class?
        if (applicationClass.name.contains("$anonfun$") || applicationClass.name.contains("$anon$")) return;

        final CtClass ctClass = makeClass(applicationClass);
        final CtClass modelClass = classPool.getCtClass("play.modules.morphia.Model");
        if (!hasAnnotation(ctClass, Entity.class.getName()) || hasAnnotation(ctClass, ByPass.class.getName()) || !ctClass.subclassOf(modelClass)) return;

        boolean addId = true;
    	boolean autoTS = hasAnnotation(ctClass, Model.AutoTimestamp.class.getName());
    	if (hasAnnotation(ctClass, NoId.class.getName())) {
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

        enhance_(ctClass, applicationClass, addId, autoTS);
    }

    /**
     * Enhance classes marked with the MongoEntity annotation.
     *
     * @param ctClass
     * @throws Exception
     */
    private void enhance_(CtClass ctClass, ApplicationClass applicationClass, boolean addId, boolean autoTS) throws Exception {
        String entityName = ctClass.getName();
        MorphiaPlugin.debug("enhancing %s ...", entityName);

        // Don't need to fully qualify types when compiling methods below
        classPool.importPackage(PACKAGE_NAME);

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
            String idType = play.modules.morphia.utils.IdGenerator.getIdTypeName();
            CtField idField = new CtField(classPool.get(idType), "_id", ctClass);
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
            CtMethod setId = CtMethod.make("protected void setId_(Object id) { _id = (" + idType + ")play.modules.morphia.utils.IdGenerator.processId(id);}", ctClass);
            ctClass.addMethod(setId);
        } else {
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
        }

        // create timestamp?
        if (autoTS) {
            ClassFile classFile = ctClass.getClassFile();
            ConstPool cp = classFile.getConstPool();
            AnnotationsAttribute attribute = new AnnotationsAttribute(cp, AnnotationsAttribute.visibleTag);
            Annotation indexAnnotation = new Annotation(cp, ClassPool.getDefault().get("com.google.code.morphia.annotations.Indexed"));
            EnumMemberValue val = new EnumMemberValue(cp);
            val.setType(IndexDirection.class.getName());
            val.setValue(IndexDirection.DESC.name());
            indexAnnotation.addMemberValue("value", val);
            attribute.addAnnotation(indexAnnotation);
            
        	Logger.trace("create timestamp fields automatically");
        	CtField createdField = new CtField(CtClass.longType, "_created", ctClass);
        	createdField.getFieldInfo().addAttribute(attribute);
        	createdField.setModifiers(Modifier.PRIVATE);
        	ctClass.addField(createdField);

        	CtField modifiedField = new CtField(CtClass.longType, "_modified", ctClass);
        	modifiedField.getFieldInfo().addAttribute(attribute);
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

        // createQuery - alias of all
        CtMethod createQuery = CtMethod.make("public static play.modules.morphia.Model.MorphiaQuery createQuery() { return all(); }",ctClass);
        ctClass.addMethod(createQuery);
        
        // q - alias of createQuery
        CtMethod q = CtMethod.make("public static play.modules.morphia.Model.MorphiaQuery q() { return all(); }",ctClass);
        ctClass.addMethod(q);

        // disableValidation
        CtMethod disableValidation = CtMethod.make("public static play.modules.morphia.Model.MorphiaQuery disableValidation() { return all().disableValidation(); }",ctClass);
        ctClass.addMethod(disableValidation);

        // find -- alias: all()
        CtMethod find = CtMethod.make("public static play.modules.morphia.Model.MorphiaQuery find() { return all(); }",ctClass);
        ctClass.addMethod(find);

        // find(String keys, Object... params)
        CtMethod find2 = CtMethod.make("public static play.modules.morphia.Model.MorphiaQuery find(String keys, java.lang.Object[] params) { return createQuery().findBy(keys, params); }",ctClass);
        ctClass.addMethod(find2);
        
        // q -- alias: filter(String, Object...)
        CtMethod q2 =  CtMethod.make("public static play.modules.morphia.Model.MorphiaQuery q(String keys, java.lang.Object value) { return createQuery().filter(keys, value); }",ctClass);
        ctClass.addMethod(q2);

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
            CtMethod findById = CtMethod.make("public static Model findById(java.lang.Object id) { return (Model)mf.findById(id); }",ctClass);
            ctClass.addMethod(findById);
        }

        // count
        CtMethod count = CtMethod.make("public static long count() { return ds().getCount(" + className + "); }", ctClass);
        ctClass.addMethod(count);

        // count (String keys, Object... params)
        CtMethod count2 = CtMethod.make("public static long count(String keys, Object[] params) { return find(keys, params).count(); }", ctClass);
        ctClass.addMethod(count2);
        
        // distinct
        CtMethod distinct = CtMethod.make(String.format("public static java.util.Set _distinct(String key) {return new java.util.HashSet(ds().getCollection(%s).distinct(key));}", className), ctClass);
        ctClass.addMethod(distinct);
        
        // max
        CtMethod max = CtMethod.make(String.format("public static Long _max(String field) {return q().max(field);}", className), ctClass);
        ctClass.addMethod(max);

        // group-max
        CtMethod groupmax = CtMethod.make(String.format("public static AggregationResult groupMax(String field, String[] groupKeys) {return q().groupMax(field, groupKeys);}", className), ctClass);
        ctClass.addMethod(groupmax);

        // min
        CtMethod min = CtMethod.make(String.format("public static Long _min(String field) {return q().min(field);}", className), ctClass);
        ctClass.addMethod(min);

        // group-min
        CtMethod groupMin = CtMethod.make(String.format("public static AggregationResult groupMin(String field, String[] groupKeys) {return q().groupMin(field, groupKeys);}", className), ctClass);
        ctClass.addMethod(groupMin);

        // average
        CtMethod average = CtMethod.make(String.format("public static Long _average(String field) {return q().average(field);}", className), ctClass);
        ctClass.addMethod(average);

        // group-average
        CtMethod groupAverage = CtMethod.make(String.format("public static AggregationResult groupAverage(String field, String[] groupKeys) {return q().groupAverage(field, groupKeys);}", className), ctClass);
        ctClass.addMethod(groupAverage);

        // sum
        CtMethod sum = CtMethod.make(String.format("public static Long _sum(String field) {return q().sum(field);}", className), ctClass);
        ctClass.addMethod(sum);

        // group-sum
        CtMethod groupSum = CtMethod.make(String.format("public static AggregationResult groupSum(String field, String[] groupKeys) {return q().groupSum(field, groupKeys);}", className), ctClass);
        ctClass.addMethod(groupSum);

        // group-count
        CtMethod groupCount = CtMethod.make(String.format("public static AggregationResult groupCount(String field, String[] groupKeys) {return q().groupCount(field, groupKeys);}", className), ctClass);
        ctClass.addMethod(groupCount);

        // deleteAll
        CtMethod deleteAll = CtMethod.make("public static long deleteAll() { return delete(all()); }",ctClass);
        ctClass.addMethod(deleteAll);

        // add @Transient to all blobs automatically
        List<String> blobs = processFields(ctClass);
        boolean hasBlobField = blobs.size() > 0;

        // enhance all getters returning blobs
        if (hasBlobField) addGetterToAllBlobFields(ctClass);
        
        // enhance saveBlob method
        if (hasBlobField) enhanceSaveDeleteBlobMethods(ctClass, blobs);
        
        // add lifecycle handling code
        addLifeCycleMethods(ctClass);

        // Done.
        applicationClass.enhancedByteCode = ctClass.toBytecode();
        ctClass.detach();
    }
    
    private void enhanceSaveDeleteBlobMethods(CtClass ctClass, List<String> blobs) throws CannotCompileException, NotFoundException {
        // -- saveBlobs
        StringBuilder sb = new StringBuilder("protected void saveBlobs() {");
        for (String blob: blobs) {
            sb.append(String.format("{Blob blob = %s; if (null != blob) {com.mongodb.gridfs.GridFSDBFile file = blob.getGridFSFile(); String name = getBlobFileName(\"%s\"); file.put(\"name\", name); file.save();}}", blob, blob));
        }
        sb.append("}");
        CtMethod method = CtMethod.make(sb.toString(), ctClass);
        ctClass.addMethod(method);
        
        String blobList = StringUtil.join(",", blobs, true);
        // -- deleteBlobs
        sb = new StringBuilder("protected void deleteBlobs() { String[] blobs = {").append(blobList).append("}; removeGridFSFiles(\"").append(ctClass.getSimpleName()).append("\", getId(), blobs);}");
        method = CtMethod.make(sb.toString(), ctClass);
        ctClass.addMethod(method);
        
        // -- deleteBlobsInBatch
        sb = new StringBuilder("protected void deleteBlobsInBatch(play.modules.morphia.Model.MorphiaQuery q) { String[] blobs = {").append(blobList).append("}; removeGridFSFiles(q, blobs);}");
        method = CtMethod.make(sb.toString(), ctClass);
        ctClass.addMethod(method);
    }

    private void addGetterToAllBlobFields(CtClass ctClass) throws CannotCompileException, NotFoundException {
        for (CtMethod method: ctClass.getMethods()) {
            // boolean isSynthetic = isSynthetic(method);
            boolean isGetter = method.getName().startsWith("get");
            boolean isReturningBlob = method.getReturnType().getName().equals("play.modules.morphia.Blob");
            // if (isSynthetic && isGetter && isReturningBlob) {
            if (isGetter && isReturningBlob) {
                String fieldName = StringUtil.lowerFirstChar(method.getName().substring(3));
                String methodStr = String.format("public Blob %s() { if (isNew()) return %s; String fileName = getBlobFileName(\"%s\"); Blob b = new Blob(fileName); return b.exists() ? b : null; }", method.getName(), fieldName, fieldName);
                CtMethod getMethod = CtMethod.make(methodStr, ctClass);
                getMethod.setModifiers(getMethod.getModifiers() | AccessFlag.SYNTHETIC);
                getMethod.setName(method.getName());
                ctClass.removeMethod(method);
                Logger.trace("adding Getter to Blob field accessor[%s] to [%s]...", getMethod.getLongName(), ctClass.getName());
                ctClass.addMethod(getMethod);
            }
        }
    }

    /*
     * 1. Add @Transparent to all Blob field
     * 2. Convert @play.modules.morphia.Model.Column to com.google.code.morphia.annotations.Property
     * 3. Return a list of names of Blob fields
     */
    private List<String> processFields(CtClass ctClass) throws NotFoundException, ClassNotFoundException {
        List<CtField> fields  = new ArrayList<CtField>();
        fields.addAll(Arrays.asList(ctClass.getDeclaredFields()));
        fields.addAll(Arrays.asList(ctClass.getFields()));
        List<String> blobs = new ArrayList<String>();
        for (CtField cf: fields) {
            CtClass ctReturnType = cf.getType();
            if (ctReturnType != null && ctReturnType.getName().equals("play.modules.morphia.Blob") &&
                    !hasAnnotation(cf, Transient.class.getName())) {
                createAnnotation(getAnnotations(cf), Transient.class);
                blobs.add(cf.getName());
            }
            
            if (Modifier.isStatic(cf.getModifiers())) continue;
            AnnotationsAttribute attr = getAnnotations(cf);
            Annotation[] aa = attr.getAnnotations();
            Annotation colA = null;
            Annotation propA = null;
            for (Annotation a: aa) {
                if (a.getTypeName().equals(Column.class.getName())) {
                    colA = a;
                } else if (a.getTypeName().equals(Property.class.getName())) {
                    propA = a;
                }
            }
            if (null == propA && null != colA) {
                MemberValue value = colA.getMemberValue("value");
                MemberValue concreteClass = colA.getMemberValue("concreteClass");
                if (null == value && null == concreteClass) continue;
                propA = new Annotation(Property.class.getName(), ctClass.getClassFile().getConstPool());
                if (null != value) propA.addMemberValue("value", value);
                if (null != concreteClass) propA.addMemberValue("concreteClass", concreteClass);
                attr.addAnnotation(propA);
            }
        }
        return blobs;
    }
    
    private void addLifeCycleMethods(CtClass ctClass) throws Exception {
        /* loop through all non-private methods including inherited one */
        for (CtMethod cm: ctClass.getMethods()) {
            enhanceLifeCycleMethods(ctClass, cm);
            enhanceLifeCycleBatchMethods(ctClass, cm);
        }
        /* loop through all private methods declared */
        for (CtMethod cm: ctClass.getDeclaredMethods()) {
            if (Modifier.isPrivate(cm.getModifiers())) {
                enhanceLifeCycleMethods(ctClass, cm);
                enhanceLifeCycleBatchMethods(ctClass, cm);
            }
        }
    }
    
    @SuppressWarnings("rawtypes")
    private void enhanceLifeCycleMethods(CtClass ctClass, CtMethod ctMethod) throws Exception {
        if (ctMethod.getParameterTypes().length > 0) return; // lifecycle method shall not have parameter
        if (ctMethod.getAnnotations().length == 0) return; // not annotated
        if (!"void".equals(ctMethod.getReturnType().getName())) return; // lifecycle method shall not return any object
        
        Class[] ca = {OnLoad.class, Loaded.class, OnAdd.class, OnUpdate.class, Added.class, Updated.class, OnDelete.class, Deleted.class, OnBatchDelete.class, BatchDeleted.class};
        AnnotationsAttribute aa = getAnnotations(ctMethod);
        for (Annotation a: aa.getAnnotations()) {
            for (Class c0: ca) {
                String nm = c0.getName();
                if (nm.equals(a.getTypeName())) {
                    // the name of lifecycle event handler, could be h_OnAdd etc.
                    String mn = "h_" + c0.getSimpleName();
                    CtMethod m0 = null;
                    try {
                        m0 = ctClass.getDeclaredMethod(mn);
                    } catch (Exception e) {
                        m0 = CtMethod.make(String.format("protected void %s(){}", mn, mn), ctClass);
                        ctClass.addMethod(m0);
                    }
                    
                    String callback = ctMethod.getName();
                    Logger.trace("Adding callback[%s] to lifecycle event handler[%s]", callback, mn);
                    m0.insertAfter(String.format("%s();", callback));
                }
            }
        }
        
    }
    
    @SuppressWarnings("rawtypes")
    private void enhanceLifeCycleBatchMethods(CtClass ctClass, CtMethod ctMethod) throws Exception {
        if (ctMethod.getAnnotations().length == 0) return; // not annotated
        if (!"void".equals(ctMethod.getReturnType().getName())) return; // lifecycle method shall not return any object
        CtClass[] params = ctMethod.getParameterTypes(); 
        if (params.length == 1) {
            CtClass p0 = params[0];
            if (!MorphiaQuery.class.getName().equals(p0.getName())) return;
        } else {
            // batch lifecycle methods shall accept one parameter: MorphhiaQuery
            return;
        }

        Class[] ca = {OnBatchDelete.class, BatchDeleted.class};
        AnnotationsAttribute aa = getAnnotations(ctMethod);
        for (Annotation a: aa.getAnnotations()) {
            for (Class c0: ca) {
                String nm = c0.getName();
                if (nm.equals(a.getTypeName())) {
                    // the name of lifecycle event handler, could be h_OnAdd etc.
                    String mn = "h_" + c0.getSimpleName();
                    CtMethod m0 = null;
                    try {
                        m0 = ctClass.getDeclaredMethod(mn);
                    } catch (Exception e) {
                        m0 = CtMethod.make(String.format("protected void %s(play.modules.morphia.Model.MorphiaQuery q){}", mn), ctClass);
                        ctClass.addMethod(m0);
                    }
                    
                    String callback = ctMethod.getName();
                    Logger.trace("Adding callback[%s] to lifecycle event handler[%s]", callback, mn);
                    m0.insertAfter(String.format("%s($$);", callback));
                }
            }
        }
    }

//    private boolean isSynthetic(CtMethod method) {
//        return (method.getMethodInfo().getAccessFlags() & AccessFlag.SYNTHETIC) != 0;
//    }
}
