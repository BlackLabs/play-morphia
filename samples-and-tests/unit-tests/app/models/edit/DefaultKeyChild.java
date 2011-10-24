package models.edit;

import play.modules.morphia.Model;

import com.google.code.morphia.annotations.Entity;

/**
 * An entity that uses the default key.
 * 
 */
@SuppressWarnings("serial")
@Entity
public class DefaultKeyChild extends Model
{
    /**
     * The name.
     */
    public String name;
}
