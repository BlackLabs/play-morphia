package controllers;

import play.*;
import play.mvc.*;
import play.data.validation.*;
import play.libs.*;
import play.cache.*;
 
import java.util.*;

import models.*;
 
public class Application extends Controller {
    
    @Before
    static void addDefaults() {
    }
 
    public static void index() {
        User user = new User("test", "test", "test");
        user.save();
        
        Account account = new Account("test", "test", "test");
        account.save();
        
        Customer customer = new Customer("test", "test", "test");
        customer.save();
    }
 
}
