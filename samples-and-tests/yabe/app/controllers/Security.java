package controllers;

import models.*;

public class Security extends Secure.Security {

    static boolean authentify(String username, String password) {
        return User.connect(username, password) != null;
    }
    
    static boolean check(String profile) {
        if("admin".equals(profile)) {
        	User u = User.find("byEmail", connected()).first();
            return u.isAdmin;
        }
        return false;
    }
    
    static void onDisconnected() {
        Application.index();
    }
    
    static void onAuthenticated() {
        Admin.index();
    }
    
}

