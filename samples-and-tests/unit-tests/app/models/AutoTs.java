package models;

import play.modules.morphia.Model;
import play.modules.morphia.Model.AutoTimestamp;

import com.google.code.morphia.annotations.Entity;

@SuppressWarnings("serial")
@Entity
@AutoTimestamp
public class AutoTs extends Model {
  public String content;
}