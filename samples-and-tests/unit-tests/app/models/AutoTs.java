package models;

import play.data.validation.Email;
import play.data.validation.Required;
import play.modules.morphia.Model;
import play.modules.morphia.AutoTimestamp;

import com.google.code.morphia.annotations.Entity;
import com.google.code.morphia.annotations.Indexed;

@SuppressWarnings("serial")
@Entity
@AutoTimestamp
public class AutoTs extends Model {
  public String content;
}