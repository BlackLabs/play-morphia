package morphia;

import java.util.ArrayList;
import java.util.List;

import play.modules.morphia.utils.StringUtil;

public class Filter {

    public static class Option {
        public String name;
        public boolean checked;

        private Option(String name, boolean checked) {
            this.name = name;
            this.checked = checked;
        }
    }

    public String property;
    public List<Option> values = new ArrayList<Option>();

    public boolean hasOption(String name) {
        for (Option o : values) {
            if (o.name.equals(name))
                return true;
        }
        return false;
    }

    public void addOption(String name) {
        if (!hasOption(name))
            values.add(new Option(name, false));
    }

    @Override
    public String toString() {
        List<String> selected = new ArrayList<String>();
        for (Option o : values) {
            if (o.checked)
                selected.add(o.name);
        }
        return selected.size() == 0 ? "" : String.format("%1$s in (%2$s)",
                property, StringUtil.join(",", selected));
    }

    public static String toString(List<Filter> filters) {
        if (null == filters)
            return "";
        List<String> l = new ArrayList<String>();
        for (Filter f : filters) {
            String s = f.toString();
            if (!StringUtil.isEmpty(s))
                l.add(s);
        }
        return null == filters ? null : StringUtil.join(" and ", l);
    }

}
