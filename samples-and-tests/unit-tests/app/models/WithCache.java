package models;

import org.mongodb.morphia.annotations.Entity;
import play.cache.CacheFor;
import play.jobs.Job;
import play.jobs.OnApplicationStart;
import play.modules.morphia.CacheEntity;
import play.modules.morphia.Model;

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
@CacheEntity
public class WithCache extends CacheTestModel {
    @OnApplicationStart
    public static class Initializer extends Job {
        @Override
        public void doJob() throws Exception {
            WithCache.deleteAll();
            List<WithCache> entities = new ArrayList<WithCache>(1000);
            for (int i = 0; i < 1000; ++i) {
                entities.add(new WithCache());
            }
            WithCache.insert(entities);
        }
    }
}
