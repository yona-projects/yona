package models;

import java.util.List;

import javax.persistence.Entity;

import play.db.ebean.Model;

@Entity
public class IssueDetailType extends Model {
	
	private static final long serialVersionUID = -8741706931323510000L;
	public String projectId;
	public String name;
	public List<String> predefinedValues;
	public boolean isRequired;
	
	public IssueDetailType(String projectId, String name) {
		this.projectId = projectId;
		this.name = name;
	}

}
