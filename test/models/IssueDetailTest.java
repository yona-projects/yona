package models;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class IssueDetailTest extends ModelTest<IssueDetail> {
    @Test
    public void setValues_1개를_선택() throws Exception {
        // Given
        Issue issue = new Issue("윈도우즈 환경에서 글 쓰기에 문제가 있습니다");

        String projectId = "1l";
		IssueDetailType detailType = getOSListTypes(projectId);

        IssueDetail issueDetail = new IssueDetail(issue, detailType);
        
        // When
        issueDetail.setValues(selectValues("windows"));
        
        // Then
        assertEquals("issue detail의 항목 이름", "OS종류", issueDetail.detailType.name);
        assertEquals("1개의 OS를 선택했을 때", "windows", issueDetail.getValues().get(0));
    }

    @Test
    public void setValues_복수개를_선택() throws Exception {
        // Given
        Issue issue = new Issue("윈도우즈 환경에서 글 쓰기에 문제가 있습니다");

        String projectId = "1l";
        IssueDetailType detailType =  getOSListTypes(projectId);

        IssueDetail issueDetail = new IssueDetail(issue, detailType);
        
        // When
        issueDetail.setValues(selectValues("windows", "linux"));
        
        // Then
        assertEquals("issue detail의 항목 이름", "OS종류", issueDetail.detailType.name);		
        assertEquals("windows와 linux 두 개의 os를 선택했을 때", 2, issueDetail.getValues().size());		
	}
    
    @Test
    public void setValues_기타값을_넣었을때() throws Exception {
        // Given
        Issue issue = new Issue("특정 브라우저에서 이상동작함");

        String projectId = "1l";
        IssueDetailType detailType = getOSListTypes(projectId);

        IssueDetail issueDetail = new IssueDetail(issue, detailType);
        
        // When
        issueDetail.setValues(selectValues("Embedded Windows"));
        
        // Then
        assertEquals("issue detail의 항목 이름", "OS종류", issueDetail.detailType.name);		
        assertEquals("기본값에 없는 값을 기타 항목에 넣었을 경우", true, issueDetail.isEtcValue);		
	}
    
    
    @Test
    public void setValues_여러개의_이슈상세항목이_존재할때() throws Exception {
        // Given
        Issue issue = new Issue("특정 브라우저에서 이상동작함");

        String projectId = "1l";
		IssueDetailType detailType_OSTYPE = getOSListTypes(projectId);

        IssueDetailType detailType_BROWSER_TYPE = new IssueDetailType(projectId, "사용 브라우저");
        detailType_BROWSER_TYPE.predefinedValues = getBrowserList();
        
        IssueDetail issueDetail_os = new IssueDetail(issue, detailType_OSTYPE);
        issueDetail_os.setValues(selectValues("windows", "linux"));

        IssueDetail issueDetail_browser = new IssueDetail(issue, detailType_BROWSER_TYPE);
        issueDetail_browser.setValues(selectValues("FIREFOX"));
        
        // When

        issue.addIssueDetails(issueDetail_browser);
        issue.addIssueDetails(issueDetail_os);

        // Then
        assertThat(issue.issueDetails.size()).isEqualTo(2);		
		
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

	private List<String> selectValues(String... osNames) {
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
