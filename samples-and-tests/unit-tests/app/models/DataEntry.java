package models;

import com.google.code.morphia.annotations.Embedded;
import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.query.CriteriaContainer;
import com.google.gson.Gson;
import org.bson.types.ObjectId;
import org.joda.time.DateMidnight;
import play.modules.morphia.Model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 24/01/13
 * Time: 10:05 AM
 * To change this template use File | Settings | File Templates.
 */
@Entity(noClassnameStored = true, value = "entry")
public class DataEntry extends Model {

    @Embedded
    public static class KV {
        public String key;
        public String value;

        public KV(String key, String val) {
            this.key = key;
            this.value = val;
        }
    }

    public DataEntry() {
    }

    public DataEntry(boolean preview) {
        this.preview = preview;
    }

    @Embedded
    private List<KV> data = new ArrayList<KV>();

    public boolean moderated;

    public boolean approved;

    public int votes;

    public boolean preview;
    
    public String code;
    
    public boolean hasImage;

    @Loaded
    void initDataContainer() {
        if (null == data) data = new ArrayList<KV>();
    }

    public void addData(String key, String val) {
        data.add(new KV(key, val.replaceAll("\n", " ").replaceAll("\r", " ").replaceAll("\t", " ")));
    }

    public boolean hasData() {
        return data.size() > 0;
    }

    public boolean hasDataValue(String value) {
        for (KV kv : data) {
            if (kv.value.equals(value))
                return true;
        }
        return false;
    }

    public String get(String key) {
        for (KV kv : data) {
            if (kv.key.equals(key)) return kv.value;
        }
        return null;
    }

    public boolean set(String key, String value) {
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).key.equals(key)) {
                data.set(i, new KV(key, value));
                save();
                return true;
            }
        }
        return false;
    }

    public List<String> keys() {
        List<String> keys = new ArrayList<String>();
        for (KV kv : data) {
            keys.add(kv.key);
        }
        return keys;
    }

    public synchronized void incrVotes() {
        votes++;
        save();
    }

    public static enum Q {
        BY_VOTES("-votes"),
        BY_DATE("-_created");

        private String fld_;

        private Q(String fld) {
            fld_ = fld;
        }

        private MorphiaQuery q(boolean preModerate, boolean withImage) {
            MorphiaQuery q = DataEntry.q().order(fld_);
            if (withImage) {
                if (preModerate) {
                    q.filter("approved", true);
                } else {
                    q.or(q.criteria("moderated").equal(false), q.and(q.criteria("moderated").equal(true), q.criteria("approved").equal(true)));
                }
                q.filter("hasImage", true);
            }

            return q;
        }

        public List<DataEntry> find(boolean preModerate, boolean withImage) {
            return q(preModerate, withImage).asList();
        }

        public List<DataEntry> findLive(boolean preModerate, boolean withImage) {
            return q(preModerate, withImage).filter("preview", false).asList();
        }

        public List<DataEntry> findPreview(boolean preModerate, boolean withImage) {
            return q(preModerate, withImage).filter("preview", true).asList();
        }

        public List<DataEntry> find(boolean preModerate, int start, int limit, boolean withImage) {
            return q(preModerate, withImage).offset(start).limit(limit).asList();
        }

        public List<DataEntry> find(boolean preModerate, int start, int limit, String q, boolean withImage) {
            if (null == q || q.equals("all")) {
                return find(preModerate, start, limit, withImage);
            }
            return q(preModerate, withImage).filter("data.value", Pattern.compile(q, Pattern.CASE_INSENSITIVE)).offset(start).limit(limit).asList();
        }

        public List<DataEntry> find(boolean preModerate, int start, int limit, String q, String q2, boolean withImage) {
            if (null == q || q.equals("all")) {
                return find(preModerate, start, limit, q2, withImage);
            }
            if (null == q2 || q2.equals("")) {
                return find(preModerate, start, limit, q, withImage);
            }

            //workaround for double filter
            List<DataEntry> entriesFirstFilter = q(preModerate, withImage).filter("data.value", Pattern.compile(q, Pattern.CASE_INSENSITIVE)).asList();
            List<DataEntry> entriesSecondFilter = new ArrayList<DataEntry>();
            List<DataEntry> entriesSecondFilterWithOffsetAndLimit = new ArrayList<DataEntry>();
            for (DataEntry e : entriesFirstFilter) {
                if (e.hasDataValue(q2))
                    entriesSecondFilter.add(e);
            }
            for (int i = start, j = 0; j < limit && i < entriesSecondFilter.size(); i++, j++) {
                entriesSecondFilterWithOffsetAndLimit.add(entriesSecondFilter.get(i));
            }
            return entriesSecondFilterWithOffsetAndLimit;
        }

        public MorphiaQuery filter(MorphiaQuery query, String key, Object value) {
            return query.filter(key, value);
        }

        public MorphiaQuery filterDate(MorphiaQuery query, long begin, long end) {
            return query.filter("_created >=", begin).filter("_created <=", end);
        }

        public MorphiaQuery filterDate(MorphiaQuery query, String date) {
            DateMidnight start = new DateMidnight();
            DateMidnight stop = new DateMidnight();

            if (date.equals("today"))
                stop = stop.plusDays(1);

            if (date.equals("yesterday"))
                start = start.minusDays(1);

            if (date.equals("thisWeek"))
                start = start.minusDays(start.getDayOfWeek() - 1);

            if (date.equals("lastWeek")) {
                start = start.minusDays(start.getDayOfWeek() + 6);
                stop = stop.minusDays(stop.getDayOfWeek() - 1);
            }

            if (date.equals("allTime"))
                return query;

            return filterDate(query, start.getMillis(), stop.getMillis());
        }

        public MorphiaQuery filterModeration(MorphiaQuery query, String moderation) {
            if (moderation.equals("pending"))
                return query.filter("moderated", false);
            if (moderation.equals("approved"))
                return query.filter("moderated", true).filter("approved", true);
            if (moderation.equals("rejected"))
                return query.filter("moderated", true).filter("approved", false);
            return query;
        }

        public MorphiaQuery search(MorphiaQuery query, String q) {
            return query.filter("data.value", Pattern.compile(q, Pattern.CASE_INSENSITIVE));
        }

        public MorphiaQuery segment(MorphiaQuery query, int start, int limit) {
            if (limit == -1)
                return query.offset(start);

            return query.offset(start).limit(limit);
        }

        public List<DataEntry> find(boolean preModerate, String target, int start, int limit, String preview, String moderated, String q, String begin, String end, String date, boolean withImage) {
            MorphiaQuery query = q(preModerate, withImage);

            if (date.equals("custom"))
                query = filterDate(query, Long.parseLong(begin), Long.parseLong(end));
            else
                query = filterDate(query, date);

            if (target != null && !target.equals("all"))
                query = filter(query, "target", target);

            query = filterModeration(query, moderated);

            if (q != null && !q.equals("") && !q.equals("undefined"))
                query = search(query, q);

            if (!preview.equals("all"))
                query = filter(query, "preview", Boolean.parseBoolean(preview));

            query = segment(query, start, limit);

            return query.asList();
        }

        public DataEntry moderate(boolean preModerate, String entryId, boolean approved, boolean withImage) {
            MorphiaQuery q = q(preModerate, withImage);
            DataEntry entry = q.filter("_id", new ObjectId(entryId)).get();
            if (null != entry) {
                entry.moderated = true;
                entry.approved = approved;
            }
            return entry;
        }
    }

    public String toJSON() {
        return new Gson().toJson(this);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<String, Object>();

        for (KV kv : data) {
            map.put(kv.key, kv.value);
        }

        return map;
    }
    
    public static enum ModerateState {NM, AP, RJ}
    public static enum IMAGE_TYPE {FB, UP, NULL}
    
    private static DataEntry newEntry(ModerateState moderate, IMAGE_TYPE image) {
        DataEntry entry = new DataEntry();
        boolean approved = moderate == ModerateState.AP;
        switch (moderate) {
            case AP: 
            case RJ: 
                entry.moderated = true;
                entry.approved = approved;
            default:
        }
        entry.hasImage = true;
        switch (image) {
            case FB:
                entry.addData("facebookImage", "fb");
                break;
            case UP:
                entry.addData("upload", "up");
                break;
            default: // do nothing
                entry.hasImage = false;
        }
        entry.code = moderate + "-" + image;
        return entry;
    }
    
    public static void prepareData() {
        DataEntry.deleteAll();
        for (ModerateState ms: ModerateState.values()) {
            for (IMAGE_TYPE img: IMAGE_TYPE.values()) {
                newEntry(ms, img).save();
            }
        }
    }
    
}
