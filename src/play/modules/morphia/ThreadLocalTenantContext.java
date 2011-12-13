package play.modules.morphia;

/**
 * TenantContext implemenation that uses ThreadLocal. Remember to clearTenant
 * after request has been handled (or is suspended). Play reuses threads for
 * multiple request and the same will also be reused if a request is suspended.
 * 
 * @author antti.poyhonen@gmail.com
 * 
 */
public class ThreadLocalTenantContext implements TenantContext {
    private static ThreadLocal<String> userLocal = new ThreadLocal<String>();

    public static void setTenant(String tenant) {
        userLocal.set(tenant);
    }

    public static void clearTenant() {
        userLocal.set(null);
    }

    public static boolean hasTenant() {
        return userLocal.get() != null;
    }

    @Override
    public String getTenant() {
        return userLocal.get();
    }

    @Override
    public String modifyCollectionName(String collectionName) {
        if (getTenant() == null) {
            return collectionName;
        }
        return getTenant() + '_' + collectionName;
    }

}
