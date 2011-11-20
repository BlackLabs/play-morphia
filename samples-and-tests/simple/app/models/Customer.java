package models;
 
import play.data.validation.Email;
import play.data.validation.Required;
import play.modules.morphia.Model;
import play.modules.morphia.Model.AutoTimestamp;

import com.google.code.morphia.annotations.Entity;
 
@SuppressWarnings("serial")
@Entity
@AutoTimestamp
public class Customer extends Model {
 
    @Email
    @Required
    public String email;
    
    @Required
    public String password;
    
    public String fullname;
    
    public boolean isAdmin;
    
    public Customer(String email, String password, String fullname) {
        this.email = email;
        this.password = password;
        this.fullname = fullname;
    }
    
    public static Customer connect(String email, String password) {
        return Customer.find("byEmailAndPassword", email, password).first();
    }
    
    public String toString() {
        return email;
    }
 
}