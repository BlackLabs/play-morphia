package models;

import org.mongodb.morphia.annotations.Entity;
import play.jobs.Job;
import play.jobs.OnApplicationStart;
import play.modules.morphia.CacheEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 29/11/12
 * Time: 11:40 AM
 * To change this template use File | Settings | File Templates.
 */
@Entity
public class WithOutCache extends CacheTestModel {
    @OnApplicationStart
    public static class Initializer extends Job {
        @Override
        public void doJob() throws Exception {
            WithOutCache.deleteAll();
            List<WithOutCache> entities = new ArrayList<WithOutCache>(1000);
            for (int i = 0; i < 1000; ++i) {
                entities.add(new WithOutCache());
            }
            WithOutCache.insert(entities);
        }
    }
}
