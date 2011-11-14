package models;
 
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
 
public class Tag {
 
//    public static List<Map> getCloud() {
//        AggregationResult r = Post.groupCount(field, groupKeys)
//        List<Map> result = Tag.find(
//            "select new map(t.name as tag, count(p.id) as pound) from Post p join p.tags as t group by t.name"
//        ).fetch();
//        return result;
//    }
    
    public static Map<String, Long> getCloud() {
        return Post._cloud("tags");
    }
    
    public static List<String> findAll() {
        return new ArrayList(Post._distinct("tags"));
    }
    
}