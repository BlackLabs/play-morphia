package controllers;

import java.util.concurrent.atomic.AtomicInteger;

import org.mongodb.morphia.Datastore;
import org.osgl._;
import org.osgl.storage.ISObject;
import org.osgl.storage.KeyGenerator;
import org.osgl.util.S;
import org.osgl.util.Str;

import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;

import play.Logger;
import play.cache.Cache;
import play.modules.morphia.Blob;
import play.modules.morphia.BlobStorageService;
import play.modules.morphia.MorphiaPlugin;
import play.mvc.Controller;

/**
 * A generic blob viewer 
 */
public class BlobController extends Controller {
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
            Str s = S.str(key);
            String ssKey = s.afterLast("-").val();
            BlobStorageService bss = BlobStorageService.getService(ssKey);
            String objKey = s.beforeFirst("-").val();
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
    
    public static void migrated() {
        render();
    }

    private static boolean migrating = false;
    private static boolean migrated = false;
    public static void migrate() {
        if (migrated) {
            migrated();
        }
        if (migrating) {
            render();
        }
        migrating = true;
        new play.jobs.Job(){
            @Override
            public void doJob() throws Exception {
                Datastore ds = MorphiaPlugin.ds();
                DBCollection col = ds.getDB().getCollection("uploads.files");
                DBCursor cur = col.find();
                int len = (int)col.count();
                int i = 1;
                final AtomicInteger errs = new AtomicInteger(0);
                BlobStorageService ss = MorphiaPlugin.bss(KeyGenerator.BY_DATE, MorphiaPlugin.defaultStorage);
                while (cur.hasNext()) {
                    DBObject obj = cur.next();
                    String s = (String)obj.get("name");
                    if (null == s) {
                        continue;
                    }
                    Logger.info("migrating %s of %s: %s...", i++, len, s);
                    final String key = s;
                    ss.migrate(key, new _.F1<Throwable, Void>() {
                        @Override
                        public Void apply(Throwable o) {
                            errs.incrementAndGet();
                            Logger.error(o, "error migrating blob: %s", key);
                            return null;
                        }
                    });
                }
                migrating = false;
                migrated = true;
            }
        }.now();
        render();
    }
}
