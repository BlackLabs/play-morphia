package play.modules.morphia.utils;


import org.mongodb.morphia.logging.Logger;
import org.mongodb.morphia.logging.LoggerFactory;

public class PlayLoggerFactory implements LoggerFactory {
    private final Logger logger = new PlayLogger();
    
    @Override
    public Logger get(Class<?> c) {
        return logger;
    }

}
