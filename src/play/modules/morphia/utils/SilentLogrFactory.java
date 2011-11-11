package play.modules.morphia.utils;

import com.google.code.morphia.logging.Logr;
import com.google.code.morphia.logging.LogrFactory;
import com.google.code.morphia.logging.SilentLogger;

public class SilentLogrFactory implements LogrFactory {

    @Override
    public Logr get(Class<?> c) {
        return new SilentLogger();
    }

}
