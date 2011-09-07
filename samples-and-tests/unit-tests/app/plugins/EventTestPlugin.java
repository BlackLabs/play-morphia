package plugins;

import java.util.HashMap;
import java.util.Map;

import play.Logger;
import play.PlayPlugin;

public class EventTestPlugin extends PlayPlugin {

    public static Map<String, Object> events = new HashMap<String, Object>();

    @Override
    public void onEvent(String message, Object context) {
        events.put(message, context);
    }
}
