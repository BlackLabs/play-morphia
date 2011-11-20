package play.modules.morphia.utils;

import com.google.code.morphia.logging.Logr;
import com.google.code.morphia.logging.LogrFactory;

public class PlayLogrFactory implements LogrFactory {
    private final Logr logger = new PlayLogr();
    
    @Override
    public Logr get(Class<?> c) {
        return logger;
    }

}
