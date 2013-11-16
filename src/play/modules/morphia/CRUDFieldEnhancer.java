package play.modules.morphia;

import javassist.CtClass;
import javassist.CtField;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.Annotation;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 16/10/13
 * Time: 9:07 PM
 * To change this template use File | Settings | File Templates.
 */
public class CRUDFieldEnhancer implements MorphiaEnhancer.FieldEnhancer {
    @Override
    public void enhance(CtField field, CtClass clz) {
        AnnotationsAttribute aa = (AnnotationsAttribute)field.getFieldInfo().getAttribute(AnnotationsAttribute.visibleTag);
        if (null == aa) {
            aa = new AnnotationsAttribute(clz.getClassFile().getConstPool(),
                                AnnotationsAttribute.visibleTag);
            field.getFieldInfo().addAttribute(aa);
        }
        Annotation ann = new Annotation("controllers.CRUD$Hidden", clz.getClassFile().getConstPool());
        aa.addAnnotation(ann);
    }
}
