package models;
 
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import play.modules.morphia.Model;
import play.modules.morphia.MorphiaPlugin;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.query.Query;
import com.google.code.morphia.query.QueryImpl;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MapReduceOutput;
 
public class Tag {
 
    private static final String m_ = "function() {this.tags.forEach(function (t) {emit (t, {count:1});});}";
    private static final String r_ = "function(v, vs){var t=0;for(var i=0;i<vs.length;++i){t+=vs[i].count}return{tag: v, count:t}}";
    
    public static List<Map<String, Integer>> getCloud() {
        return getCloud(null);
    }
    
    public static List<Map<String, Integer>> getCloud(Query<? extends Model> query) {
        List<Map<String, Integer>> result = new ArrayList<Map<String, Integer>>();
        Datastore ds = MorphiaPlugin.ds();
        DBCollection dbCol = ds.getCollection(Post.class);
        MapReduceOutput out = dbCol.mapReduce(m_, r_, null, query == null ? null : ((QueryImpl<? extends Model>)query).getQueryObject());
        for (Iterator<DBObject> itr = out.results().iterator(); itr.hasNext();) {
            DBObject dbo = itr.next();
            DBObject k_v = (DBObject)dbo.get("value");
            Map<String, Integer> m = new HashMap<String, Integer>();
            m.put((String)k_v.get("tag"), ((Double)k_v.get("count")).intValue());
            result.add(m);
        }
        return result;
    }
    
    public static List<String> findAll() {
        return findAll(null);
    }
    
    public static List<String> findAll(Query<? extends Model> query) {
        List<String> l = new ArrayList<String>();
        Datastore ds = MorphiaPlugin.ds();
        DBCollection dbCol = ds.getCollection(Post.class);
        MapReduceOutput out = dbCol.mapReduce(m_, r_, null, query == null ? null : ((QueryImpl<? extends Model>)query).getQueryObject());
        for (Iterator<DBObject> itr = out.results().iterator(); itr.hasNext();) {
            DBObject dbo = itr.next();
            DBObject k_v = (DBObject)dbo.get("value");
            String t = (String)k_v.get("tag");
            if (t != null && !"".equals(t)) l.add(t);
        }
        return l;
    }
 
}