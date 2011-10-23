package models;

import play.data.validation.Email;
import play.data.validation.Required;
import play.modules.morphia.Model;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Indexed;

@SuppressWarnings("serial")
@Entity
public class Account extends Model {

    @Required
    @Indexed(unique = true)
    public String login;

    @Required
    @Email
    public String email;
    
    public String region;
    
    public int age;
    
    public int score;
    
    public String department;

    public Account(String login, String email) {
        this(login, email, "AU");
    }
    
    public Account(String login, String email, String region) {
        this(login, email, region, "IT");
    }
    
    public Account(String login, String email, String region, String department) {
        this(login, email, region, department, 50, 80);
    }
    
    public Account(String login, String email, String region, String department, int age, int score) {
        this.login = login;
        this.email = email;
        this.region = region;
        this.department = department;
        this.age = age;
        this.score = score;
    }
    
}