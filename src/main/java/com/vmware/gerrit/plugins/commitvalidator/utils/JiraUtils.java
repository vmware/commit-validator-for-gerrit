package com.vmware.gerrit.plugins.commitvalidator.utils;

import com.vmware.gerrit.plugins.commitvalidator.entities.InvalidEntryException;
import net.rcarz.jiraclient.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;

public class JiraUtils {
    private String url;
    private String username;
    private String password;

    public JiraUtils(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public boolean isIssueIdValid(String issueId, List<String> allowedStatuses) throws InvalidEntryException, JiraException {
        BasicCredentials creds = new BasicCredentials(username, password);
        JiraClient jira = new JiraClient(url, creds);

        try {
            jira.getRestClient().get(url);
        } catch (RestException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        Issue issue = jira.getIssue(issueId);
        if (issue == null) {
            throw new InvalidEntryException("No Jira issue is found with given ID:" + issueId);
        }

        if (allowedStatuses != null) {
            //log.info(">> Issue status {}", issue.getStatus().getName());
            if (!allowedStatuses.contains(issue.getStatus().getName().toUpperCase())) {
                throw new InvalidEntryException(String.format("Jira issue %s is in %s status. But allowed statuses are:%s", issueId, issue.getStatus().getName().toUpperCase(), allowedStatuses.toString()));
            }
        }
        return true;
    }
}
