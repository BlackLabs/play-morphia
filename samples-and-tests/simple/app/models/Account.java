package models;
 
import play.data.validation.Email;
import play.data.validation.Required;
import play.modules.morphia.Model;
import play.modules.morphia.Model.AutoTimestamp;
import play.modules.morphia.Model.Datasource;

import com.google.code.morphia.annotations.Entity;
 
@SuppressWarnings("serial")
@Entity
@AutoTimestamp
@Datasource(name="cluster2")
public class Account extends Model {
 
    @Email
    @Required
    public String email;
    
    @Required
    public String password;
    
    public String fullname;
    
    public boolean isAdmin;
    
    public Account(String email, String password, String fullname) {
        this.email = email;
        this.password = password;
        this.fullname = fullname;
    }
    
    public static Account connect(String email, String password) {
        return Account.find("byEmailAndPassword", email, password).first();
    }
    
    public String toString() {
        return email;
    }
 
}