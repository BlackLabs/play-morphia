package play.modules.morphia;

import java.util.HashSet;

import play.Play;

import com.google.code.morphia.DatastoreImpl;
import com.google.code.morphia.Morphia;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;

/**
 * Provides multitenancy support to play-morphia by overriding Morphia's default
 * DatastoreImpl. Multitenancy support in current implementation means that
 * collection names can be changed dynamically based on the current tenant. This
 * implementation relies on the assumption that Morphia will always use the
 * getCollection-method before operating on a collection.
 * 
 * @author antti.poyhonen@gmail.com
 * 
 */
public class TenantDatastore extends DatastoreImpl {

    private HashSet<String> excludedClasses;
    private TenantContext tenantContext;
    private boolean multitenantMode;

    public TenantDatastore(Morphia morphia, Mongo mongo, String dbName, String username, char[] password) {
        super(morphia, mongo, dbName, username, password);
        this.multitenantMode = "true".equals(Play.configuration.getProperty("morphia.multitenantMode", "false"));
        this.tenantContext = null;
        this.excludedClasses = new HashSet<String>();
        for (String className : Play.configuration.getProperty("morphia.nonTenantClasses", "").split(",")) {
            if (className.trim().length() > 0) {
                excludedClasses.add(className.trim());
            }
        }
    }

    public TenantContext getTenantContext() {
        return tenantContext;
    }

    public void setTenantContext(TenantContext tenantContext) {
        this.tenantContext = tenantContext;
    }

    public boolean isMultitenantMode() {
        return multitenantMode;
    }

    public void setMultitenantMode(boolean multitenantMode) {
        this.multitenantMode = multitenantMode;
    }

    @Override
    public DBCollection getCollection(Class clazz) {
        if (!isMultitenantMode() || excludedClasses.contains(clazz.getName())) {
            return super.getCollection(clazz);
        }

        String tenant = null;
        if (tenantContext != null) {
            tenant = tenantContext.getTenant();
        }

        if (tenant == null) {
            String msg = "Current tenant not set while operating on tenant specific class: " + clazz.getName();
            throw new IllegalStateException(msg);
        } else {
            DBCollection coll = super.getCollection(clazz);
            String collName = tenantContext.modifyCollectionName(coll.getName());
            return getDB().getCollection(collName);
        }
    }

}
