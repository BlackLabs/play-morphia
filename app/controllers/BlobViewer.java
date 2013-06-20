package controllers;

import com.greenlaw110.storage.ISObject;
import com.greenlaw110.util.F;
import com.greenlaw110.util.S;
import play.cache.Cache;
import play.modules.morphia.Blob;
import play.modules.morphia.BlobStorageService;
import play.mvc.Controller;

/**
 * A generic blob viewer 
 */
public class BlobViewer extends Controller {
    public static void view(String key) {
        notFoundIfNull(key);
        Blob blob;
        if (key.endsWith(Blob.TMP_ID_SUFFIX)) {
            blob = Cache.get(key, Blob.class);
            notFoundIfNull(blob);
        } else {
            if (!key.contains("-")) {
                ISObject sobj = Cache.get(key, ISObject.class);
                notFoundIfNull(sobj);
                response.setContentTypeIfNotSet(sobj.getAttribute(ISObject.ATTR_CONTENT_TYPE));
                renderBinary(sobj.asInputStream());
            }
            F.Str s = S.str(key);
            String ssKey = s.afterLast("-").get();
            BlobStorageService bss = BlobStorageService.getService(ssKey);
            String objKey = s.beforeFirst("-").get();
            ISObject sobj = bss.get(objKey);
            if (null == objKey) {
                sobj = Cache.get(key, ISObject.class);
            }
            notFoundIfNull(sobj);
            blob = new Blob(sobj, bss);
        }
        if (!blob.exists()) {
            notFound();
        }
        response.setContentTypeIfNotSet(blob.type());
        renderBinary(blob.get());
    }
}
