package models.edit;

import java.util.List;

import play.modules.morphia.Model;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Reference;

/**
 * An entity that acts as parent and keeps references to single and multiple
 * children.
 * 
 */
@SuppressWarnings("serial")
@Entity
public class Parent extends Model
{
    /**
     * The reference to the child that has custom key defined.
     */
    @Reference
    public CustomKeyChild customKeyChild;

    /**
     * The reference to the child that has default key defined.
     */
    @Reference
    public DefaultKeyChild defaultKeyChild;

    /**
     * The reference to the children that have custom keys defined.
     */
    @Reference
    public List<CustomKeyChild> customKeyChildren;

    /**
     * The reference to the children that have default keys defined.
     */
    @Reference
    public List<DefaultKeyChild> defaultKeyChildren;

}
