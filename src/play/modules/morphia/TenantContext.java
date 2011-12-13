package play.modules.morphia;

/**
 * Provides play-morphia information about the currently operated tenant.
 * Implementations of this class could be something that holds the current
 * tenant in Request.args, or it could hold the current tenant in ThreadLocal.
 * 
 * @author antti.poyhonen@gmail.com
 * 
 */
public interface TenantContext {

    /**
     * @return current tenant id, or null if tenant isn't known currently
     */
    public String getTenant();

    /**
     * Modifies the current collectionName to be tenant specific. Not called for
     * classes specified in configuration option "morphia.nonTenantClasses"
     * 
     * @param collectionName
     *            original collection name based on morphia annotation mappings
     * @return collectionName used for the current tenant (for example:
     *         getTenant()+'_'+collectionName).
     */
    public String modifyCollectionName(String collectionName);
}
