package models;

import play.modules.morphia.Model;

import java.util.List;
import java.util.Random;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 29/11/12
 * Time: 11:41 AM
 * To change this template use File | Settings | File Templates.
 */
public class CacheTestModel extends Model {

    protected CacheTestModel() {
        f1 = random(8);
        f2 = new Random().nextInt(100);
        f3 = random(22);
        f4 = false;
        f5 = E.e2;
    }

    public enum E {
        e1, e2
    }

    public String f1;
    public int f2;
    public String f3;
    public boolean f4;
    public E f5;

    private static String random(int len) {
        final char[] chars = {'0', '1', '2', '3', '4',
                '5', '6', '7', '8', '9', '$', '#', '^', '&', '_',
                'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j',
                'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't',
                'u', 'v', 'w', 'x', 'y', 'z',
                'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J',
                'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T',
                'U', 'V', 'W', 'X', 'Y', 'Z',
                '~', '!', '@'};

        final int max = chars.length;
        Random r = new Random();
        StringBuffer sb = new StringBuffer(len);
        while(len-- > 0) {
            int i = r.nextInt(max);
            sb.append(chars[i]);
        }
        return sb.toString();
    }

    private static String random() {
        return random(8);
    }
}
