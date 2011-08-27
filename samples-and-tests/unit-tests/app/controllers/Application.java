package controllers;

import java.io.File;

import models.User;

import org.apache.commons.lang.RandomStringUtils;

import play.modules.morphia.Blob;
import play.mvc.Controller;

public class Application extends Controller {

    public static void index() {
        session.clear();
        renderText(session.getId());
    }

    public static void uploadImage(File image) {
        notFoundIfNull(image);
        User u = new User();
        u.name = RandomStringUtils.randomAlphabetic(5);
        u.photo = new Blob(image, "image/jpeg");
        u = u.save();

        renderText(u.getId());
    }

    public static void getImage(String id) {
        User u = User.findById(id);
        notFoundIfNull(u);
        notFoundIfNull(u.photo);
        renderBinary(u.photo.get());
    }
}
