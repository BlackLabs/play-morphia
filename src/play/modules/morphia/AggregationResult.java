package play.modules.morphia;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.mongodb.BasicDBObject;

public class AggregationResult {
    private List<BasicDBObject> r_ = null;
    private Class<? extends Model> c_ = null;
    private String def_ = null;
    private String mappedDef_ = null;

    public AggregationResult(List<BasicDBObject> r, String aggregationField, Class<? extends Model> modelClass) {
        if (null == r || null == aggregationField) throw new NullPointerException();
        r_ = r;
        def_ = aggregationField;
        c_ = modelClass;
        mappedDef_ = MorphiaPlugin.mongoColName(modelClass, aggregationField);
    }

    private static boolean isEqual_(Object a, Object b) {
        if (a == b) return true;
        return (null == a) ? (null == b) : (null == b) ? false : a.equals(b);
    }

    public Long getResult() {
        return r_.size() > 0 ? r_.get(0).getLong(mappedDef_) : null;
    }

    public Long getResult(String groupKeys, Object... groupValues) {
    if (StringUtils.isBlank(groupKeys)) {
            if (groupValues.length == 0) return getResult();
            throw new IllegalArgumentException("the number of group keys does not match the number of group values");
        }
        String[] sa = groupKeys.split("(And|[,;\\s])");
        if (sa.length != groupValues.length) throw new IllegalArgumentException("the number of group keys does not match the number of group values");
        for (BasicDBObject r: r_) {
            boolean found = true;
            String s;
            for (int i = 0; i < sa.length; ++i) {
                s = MorphiaPlugin.mongoColName(c_, sa[i]);
                if (!isEqual_(r.get(s), groupValues[i])) {
                    found = false;
                    break;
                }
            }
            if (found) return r.getLong(mappedDef_);
        }
        return null;
    }
    
    public Map<String, Long> asNumberMap() {
        Map<String, Long> m = new HashMap(r_.size());
        for (BasicDBObject r : r_) {
            Collection<?> c = r.values();
            Iterator itr = c.iterator();
            String k = itr.next().toString();
            String s = itr.next().toString();
            float f = Float.parseFloat(s);
            long l = (long)f;
            m.put(k, l);
        }
        return m;
    }
    
    public List<BasicDBObject> raw() {
        return r_;
    }
}
