package morphia;

import org.osgl.util.S;

import java.util.ArrayList;
import java.util.List;

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
        return selected.size() == 0 ? "" : S.fmt("%1$s in (%2$s)",
                property, S.join(",", selected));
    }

    public static String toString(List<Filter> filters) {
        if (null == filters)
            return "";
        List<String> l = new ArrayList<String>();
        for (Filter f : filters) {
            String s = f.toString();
            if (!S.empty(s))
                l.add(s);
        }
        return null == filters ? null : S.join(" and ", l);
    }

}
