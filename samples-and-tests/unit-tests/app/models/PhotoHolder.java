package models;

import play.modules.morphia.Blob;
import play.modules.morphia.Model;

/**
 * Created with IntelliJ IDEA.
 * User: luog
 * Date: 10/06/12
 * Time: 1:58 PM
 * To change this template use File | Settings | File Templates.
 */
public class PhotoHolder extends Model {
    public Blob photo;
}
