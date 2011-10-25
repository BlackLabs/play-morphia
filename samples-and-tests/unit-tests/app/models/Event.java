package models;

import java.util.HashMap;
import java.util.Map;

public class Event {
    private static Map<Event, Integer> events = new HashMap<Event, Integer>();
    
    public static void reset() {
        events.clear();
    }
    
    public Object key;
    public Class<?> type;

    private Event(Object id, Class<?> event) {
        key = id;
        this.type = event;
    }
    
    @Override
    public int hashCode() {
        return key.hashCode() * 31 + type.hashCode();
    }
    
    @Override
    public String toString() {
        return String.format("[%s]%s", type.getSimpleName(), key);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj instanceof Event) {
            Event that = (Event)obj;
            return that.key.equals(this.key) && that.type.equals(this.type);
        }
        return false;
    }
    
    public static Event newEvent(Object id, Class<?> type) {
        Event e = new Event(id, type);
        if (!events.containsKey(e)) {
            events.put(e, 1);
        } else {
            events.put(e, events.get(e) + 1);
        }
        return e;
    }
    
    public static int count(Object id, Class<?> type) {
        Event e = new Event(id, type);
        return events.containsKey(e) ? events.get(new Event(id, type)) : 0;
    }

}
