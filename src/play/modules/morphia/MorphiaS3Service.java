package play.modules.morphia;

import org.osgl.storage.KeyGenerator;
import org.osgl.storage.impl.S3Service;
import org.osgl.util.C;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 26/06/13
 * Time: 9:15 AM
 * To change this template use File | Settings | File Templates.
 */
public class MorphiaS3Service extends S3Service {

    public MorphiaS3Service(KeyGenerator keygen) {
        super(keygen);
    }

    @Override
    public void configure(Map<String, String> conf) {
        Map<String, String> conf1 = C.newMap(conf);
        if (MorphiaPlugin.migrateData) {
            conf1.put(S3Service.CONF_GET_META_ONLY, "true");
        } else {
            conf1.put(S3Service.CONF_GET_NO_GET, "true");
        }
        super.configure(conf1);
    }
}
