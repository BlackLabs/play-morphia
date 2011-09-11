package play.modules.morphia;

import java.util.List;

import play.modules.morphia.utils.StringUtil;

import com.mongodb.CommandResult;

public class AggregationResult {
    private List<CommandResult> r_ = null;
    private String def_ = null;
    
    public AggregationResult(List<CommandResult> r, String aggregationField) {
        if (null == r || null == aggregationField) throw new NullPointerException();
        r_ = r;
        def_ = aggregationField;
    }
    
    private static boolean isEqual_(Object a, Object b) {
        if (a == b) return true;
        return (null == a) ? (null == b) : (null == b) ? false : a.equals(b);
    }
    
    public Long getResult() {
        return r_.size() > 0 ? r_.get(0).getLong(def_) : null;
    }
    
    public Long getResult(String groupKeys, Object... groupValues) {
        if (StringUtil.isEmpty(groupKeys)) {
            if (groupValues.length == 0) return getResult();
            throw new IllegalArgumentException("the number of group keys does not match the number of group values");
        }
        String[] sa = groupKeys.split("(And|[,;\\s])");
        if (sa.length != groupValues.length) throw new IllegalArgumentException("the number of group keys does not match the number of group values");
        for (CommandResult r: r_) {
            boolean found = true;
            for (int i = 0; i < sa.length; ++i) {
                if (!isEqual_(r.get(sa[i]), groupValues[i])) {
                    found = false;
                    break;
                }
            }
            if (found) return r.getLong(def_);
        }
        return null;
    }
    
    public List<CommandResult> raw() {
        return r_;
    }
}
