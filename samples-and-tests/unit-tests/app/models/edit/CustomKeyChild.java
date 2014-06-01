package models.edit;

import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

import play.modules.morphia.Model;

/**
 * An entity that has a custom key annotated with @Id.
 *
 */
@SuppressWarnings("serial")
@Entity
public class CustomKeyChild extends Model
{
    /**
     * The custom key
     */
    @Id
    public String key;

    /**
     * The name.
     */
    public String name;
}
