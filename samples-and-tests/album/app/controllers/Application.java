package controllers;

import models.Album;
import models.Photo;
import play.cache.Cache;
import play.modules.morphia.Blob;
import play.mvc.Controller;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class Application extends Controller {

    public static void index() {
        List<Album> albums = Album.findAll();
        render(albums);
    }

    public static void album(String albumId) {
        Album album = Album.findById(albumId);
        notFoundIfNull(album);

        render(album);
    }

    public static void albumForm(String albumId) {
        Album album;
        if (null != albumId) {
            album = Album.findById(albumId);
            notFoundIfNull(album);
        } else {
            album = new Album();
        }
        render(album);
    }

    public static void saveAlbum(Album album) {
        album.save();
        album(album.getIdAsStr());
    }
    
    private static String photoCacheKey() {
        return session.getId() + ":photo";
    }
    
    public static void newPhotoForm(String albumId) {
        Album album = Album.findById(albumId);
        notFoundIfNull(album);

        Photo photo = new Photo(albumId);
        Cache.set(photoCacheKey(), photo);
        render(album);
    }

    public static void deletePhoto(String photoId) {
        Photo photo = Photo.findById(photoId);
        notFoundIfNull(photo);
        photo.delete();
        album(photo.albumId);
    }

    public static void upload(File file) {
        Photo photo = Cache.get(photoCacheKey(), Photo.class);
        notFoundIfNull(photo);
        
        photo.blob = new Blob(file);
    }
    
    public static void savePhoto(String desc, String tags) {
        Photo photo = Cache.get(photoCacheKey(), Photo.class);
        notFoundIfNull(photo);

        photo.desc = desc;
        if (null != tags) {
            photo.tags.addAll(Arrays.asList(tags.split("[,; ]+")));
        }
        photo.save();

        Cache.delete(photoCacheKey());
        
        album(photo.albumId);
    }

}