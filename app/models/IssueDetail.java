package models;

import java.util.List;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

import play.db.ebean.Model;

@Entity
public class IssueDetail extends Model {

	private static final long serialVersionUID = -5771402882075775490L;

	@ManyToOne
	Issue issue;
	IssueDetailType detailType;
	private List<String> values;
	boolean isEtcValue = false;
	
	public IssueDetail(Issue issue, IssueDetailType detailType) {
		this.issue = issue;
		this.detailType = detailType;
	}

	public List<String> getValues() {
		return values;
	}

	public void setValues(List<String> values) {
		for(String selectedValue: values){
			if( !detailType.predefinedValues.contains(selectedValue) ){
				this.isEtcValue = true;
			}
		}
		this.values = values;
	}
}

