package play.modules.morphia.utils;


import org.mongodb.morphia.logging.LoggerFactory;
import org.mongodb.morphia.logging.Logger;
import org.mongodb.morphia.logging.SilentLogger;

public class SilentLoggerFactory implements LoggerFactory {

    @Override
    public Logger get(Class<?> c) {
        return new SilentLogger();
    }

}
