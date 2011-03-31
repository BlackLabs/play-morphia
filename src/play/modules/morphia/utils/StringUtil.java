package play.modules.morphia.utils;

import java.util.Collection;
import java.util.Iterator;

public class StringUtil {
    public static String join(String separator, Collection<?> list) {
        return join(separator, null, null, list);
    }

    public static String join(String separator, String prefix, String suffix,
            Collection<?> list) {
        StringBuilder sb = new StringBuilder();

        if (null != prefix)
            sb.append(prefix).append(separator);

        Iterator<?> itr = list.iterator();
        if (itr.hasNext())
            sb.append(itr.next());
        while (itr.hasNext()) {
            sb.append(separator).append(itr.next());
        }

        if (null != suffix)
            sb.append(separator).append(suffix);
        return sb.toString();
    }

    public static String join(String separator, String... list) {
        StringBuilder sb = new StringBuilder();

        if (list.length > 0) {
            sb.append(list[0]);
            for (int i = 1; i < list.length; ++i)
                sb.append(separator).append(list[i]);
        }

        return sb.toString();
    }

    public static boolean isEmpty(String s) {
        if (null == s || "".equals(s.trim()))
            return true;
        return false;
    }

    public static final int IGNORECASE = 0x00001000;
    public static final int IGNORESPACE = 0x00002000;

    public static boolean isEqual(String s1, String s2) {
        return isEqual(s1, s2, 0);
    }

    public static boolean isEqual(String s1, String s2, int modifier) {
        if (null == s1) {
            return s2 == null;
        }
        if (null == s2)
            return false;
        if ((modifier & IGNORESPACE) != 0) {
            s1 = s1.trim();
            s2 = s2.trim();
        }
        if ((modifier & IGNORECASE) != 0) {
            return s1.equalsIgnoreCase(s2);
        } else {
            return s1.equals(s2);
        }
    }

}
