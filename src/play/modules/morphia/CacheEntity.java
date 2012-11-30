package play.modules.morphia;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Cache a model entity by ID
 *
 * <p>If a time is not specified, the entity will be cached for 1 hour by default.
 *
 * <p>Example: <code>@CacheEntity("1h")</code>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CacheEntity {
    String value() default "1h";
}
