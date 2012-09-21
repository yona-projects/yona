package models;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import junit.framework.TestCase;

public class IssueDetailTypeTest extends TestCase {

	@Test
	public void testFindIssueType() throws Exception {
	}
	
	private IssueDetailType getOSListTypes(String projectId) {
		IssueDetailType detailType_OSTYPE = new IssueDetailType(projectId, "OS종류");
        detailType_OSTYPE.predefinedValues = getOsList();
		return detailType_OSTYPE;
	}
    
	private List<String> getBrowserList() {
		List<String> browserList = new ArrayList<String>();
		browserList.add("IE8");
		browserList.add("CHROME"); 
		browserList.add("FIREFOX");
		return browserList;
	}

	private List<String> selectOS(String... osNames) {
		List<String> selectedOS = new ArrayList<String>();
        for (String osName : osNames) {
        	selectedOS.add(osName);
		}
		return selectedOS;
	}

	private List<String> getOsList() {
		List<String> osList = new ArrayList<String>();
        osList.add("WIDNWOS");
        osList.add("MAC OS"); 
        osList.add("LINUX");
		return osList;
	}

}
