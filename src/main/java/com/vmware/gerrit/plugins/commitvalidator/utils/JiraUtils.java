package com.vmware.gerrit.plugins.commitvalidator.utils;

import lombok.extern.slf4j.Slf4j;
import net.rcarz.jiraclient.BasicCredentials;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import org.apache.commons.lang.StringUtils;

@Slf4j
public class JiraUtils {
    private String url;
    private String username;
    private String password;

    public JiraUtils(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public boolean isIssueIdValid(String issueId, String rejectedStatus) {
        BasicCredentials creds = new BasicCredentials(username, password);
        JiraClient jira = new JiraClient(url, creds);
        try {
            Issue issue = jira.getIssue(issueId);
            if (issue == null) {
                return false;
            }

            if (!StringUtils.isEmpty(rejectedStatus)) {
                log.info(">> Issue status {}", issue.getStatus().getName());
                if (issue.getStatus().getName().equalsIgnoreCase(rejectedStatus)) {
                    return false;
                }
            }
            return true;
        } catch (JiraException e) {
            log.warn("Error while fetching the jira issue details for {}, error:", issueId, e.getMessage());
            return false;
        }
    }
}
