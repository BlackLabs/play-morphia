package play.modules.morphia.utils;

import org.osgl.util.S;

import java.io.File;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 16/11/13
 * Time: 8:33 PM
 * To change this template use File | Settings | File Templates.
 */
public enum MimeTypes {
    _;

    private Map<String, String> m = new HashMap<String, String>();

    private MimeTypes() {
        p(".doc:application/msword");
        p(".dot:application/msword");
        p(".docx:application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        p(".dotx:application/vnd.openxmlformats-officedocument.wordprocessingml.template");
        p(".docm:application/vnd.ms-word.document.macroEnabled.12");
        p(".dotm:application/vnd.ms-word.template.macroEnabled.12");
        p(".ppt:application/vnd.ms-powerpoint");
        p(".pot:application/vnd.ms-powerpoint");
        p(".pps:application/vnd.ms-powerpoint");
        p(".ppa:application/vnd.ms-powerpoint");
        p(".potx:application/vnd.openxmlformats-officedocument.presentationml.template");
        p(".ppsx:application/vnd.openxmlformats-officedocument.presentationml.slideshow");
        p(".pptx:application/vnd.openxmlformats-officedocument.presentationml.presentation");
        p(".sldx:application/vnd.openxmlformats-officedocument.presentationml.slide");
        p(".ppam:application/vnd.ms-powerpoint.addin.macroEnabled.12");
        p(".pptm:application/vnd.ms-powerpoint.presentation.macroEnabled.12");
        p(".potm:application/vnd.ms-powerpoint.template.macroEnabled.12");
        p(".ppsm:application/vnd.ms-powerpoint.slideshow.macroEnabled.12");
        p(".xla:application/vnd.ms-excel");
        p(".xls:application/vnd.ms-excel");
        p(".xlt:application/vnd.ms-excel");
        p(".xlam:application/vnd.ms-excel.addin.macroEnabled.12");
        p(".xlsb:application/vnd.ms-excel.sheet.binary.macroEnabled.12");
        p(".xlsm:application/vnd.ms-excel.sheet.macroEnabled.12");
        p(".xltm:application/vnd.ms-excel.template.macroEnabled.12");
        p(".xlsx:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        p(".xltx:application/vnd.openxmlformats-officedocument.spreadsheetml.template");
    }

    private void p(String s) {
        String[] sa = s.split(":");
        m.put(sa[0], sa[1]);
    }

    private String get(String k) {
        return m.get(k);
    }

    public static String probe(File file) {
        return probe(file.getName());
    }

    public static String probe(String fileName) {
        // try JDK util first
        String type = URLConnection.guessContentTypeFromName(fileName);
        if (null == type) {
            String suffix = "." + S.str(fileName).afterLast(".").get();
            type = _.get(suffix);
        }
        return type;
    }
}
