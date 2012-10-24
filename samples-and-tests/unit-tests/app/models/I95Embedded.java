package models;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 24/10/12
 * Time: 11:14 AM
 * To change this template use File | Settings | File Templates.
 */
public class I95Embedded extends I95Base {
    public String a;
    public String b;

    public I95Embedded(String A, String B, String C) {
        a = A;
        b = B;
        c = C;
    }
}
