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

    public Account(String login, String email) {
        this.login = login;
        this.email = email;
    }

}