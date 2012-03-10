package play.modules.morphia.utils;

import java.util.Collection;
import java.util.Iterator;

public class StringUtil {
    public static String join(String separator, Collection<?> list) {
        return join(separator, null, null, list);
    }

    public static String join(String separator, Collection<?> list, boolean quoted) {
        return join(separator, null, null, list, quoted);
    }

    public static String join(String separator, String prefix, String suffix, Collection<?> list, boolean quoted) {
        StringBuilder sb = new StringBuilder();

        if (null != prefix)
            sb.append(prefix).append(separator);

        Iterator<?> itr = list.iterator();
        if (itr.hasNext()) {
            if (quoted) sb.append("\"");
            sb.append(itr.next());
            if (quoted) sb.append("\"");
        }
        while (itr.hasNext()) {
            sb.append(separator);
            if (quoted) sb.append("\"");
            sb.append(itr.next());
            if (quoted) sb.append("\"");
        }

        if (null != suffix)
            sb.append(separator).append(suffix);
        return sb.toString();
    }

    public static String join(String separator, String prefix, String suffix,
            Collection<?> list) {
        return join(separator, prefix, suffix, list, false);
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

    public static String upperFirstChar(String s) {
        if (StringUtil.isEmpty(s)) return s;
        String init = s.substring(0, 1);
        String rest = s.substring(1);
        return String.format("%s%s", init.toUpperCase(), rest);
    }

    public static String lowerFirstChar(String s) {
        if (StringUtil.isEmpty(s)) return s;
        String init = s.substring(0, 1);
        String rest = s.substring(1);
        return String.format("%s%s", init.toLowerCase(), rest);
    }

    public static String trim(String s) {
        if (null == s) return null;
        return s.trim();
    }

    private static void echo(String msg, Object... args) {
        System.out.println(String.format(msg, args));
    }

    public static void main(String[] sa) {
        echo("%s", lowerFirstChar(""));
        echo("%s", lowerFirstChar("s"));
        echo("%s", lowerFirstChar("S"));
        echo("%s", lowerFirstChar("Photo"));
    }
}
