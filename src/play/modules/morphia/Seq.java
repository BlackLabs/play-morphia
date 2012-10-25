package play.modules.morphia;

import com.google.code.morphia.Datastore;
import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Id;
import com.google.code.morphia.annotations.PrePersist;
import com.google.code.morphia.annotations.Property;
import com.google.code.morphia.query.Query;
import com.google.code.morphia.query.UpdateOperations;

@Entity(value="ids", noClassnameStored=true)
public class Seq {
    final @Id
    String id;

    protected Long value = 1L;

    @PrePersist void prePersist() {
        if (value != 1L) throw new IllegalStateException("cannot save Seq");
    }

    public Seq(String name) {
        id = name;
    }

    protected Seq() {
        id = "";
    }

    public Long getValue() {
        return value;
    }

    private int numdigits(long num) {
        if (num < 10) return 1;
        int len = 0;

        while(num >= 10)
        {
            num = num/10;
            len++;
        }

        return len;
    }

    public String getAsString(int digits) {
        return getAsString("", digits);
    }

    public String getAsString(String prefix, int digits) {
        StringBuilder sb = new StringBuilder(prefix);
        int mydigits = numdigits(value);
        if (digits < mydigits) {
            sb.append(value);
        } else {
            int delta = digits - mydigits;
            for (int i = 0; i < delta; ++i) {
                sb.append(0);
            }
            sb.append(value);
        }
        return sb.toString();
    }

    public static long nextValue() {
        return nextValue(Seq.class);
    }

    public static Seq next() {
        return next(Seq.class);
    }

    public static long nextValue(Class<?> clz) {
        return nextValue(clz.getName());
    }

    public static Seq next(Class<?> clz) {
        return next(clz.getName());
    }

    public static long nextValue(String name) {
        return next(name).getValue();
    }

    public static Seq next(String name) {
        Datastore ds = MorphiaPlugin.ds();
        Query<Seq> q = ds.find(Seq.class, "_id", name);
        UpdateOperations<Seq> o = ds.createUpdateOperations(Seq.class).inc("value");
        Seq newId = ds.findAndModify(q, o);
        if (null == newId) {
            newId = new Seq(name);
            ds.save(newId);
        }

        return newId;
    }

    public static void initSelf() {
        init(Seq.class);
    }

    public static void init(String name) {
        Datastore ds = MorphiaPlugin.ds();
        Query<Seq> q = ds.find(Seq.class, "_id", name);
        if (0 == q.countAll()) {
            Seq newId = new Seq(name);
            ds.save(newId);
        }

        return;
    }

    public static void init(Class<?> clz) {
        init(clz.getName());
    }

}
