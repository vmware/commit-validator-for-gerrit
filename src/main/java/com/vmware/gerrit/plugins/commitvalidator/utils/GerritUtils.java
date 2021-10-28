package com.vmware.gerrit.plugins.commitvalidator.utils;

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.RestApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GerritUtils {
    private GerritApi gerritApi;

    public GerritUtils(GerritApi gerritApi) {
        this.gerritApi = gerritApi;
    }

    public List<String> getAllUsers(List<String> additionalCRApprovers) throws RestApiException {
        // Get all users of the
        List<String> allUsernames = new ArrayList<>();

        for (String additionalApprover : additionalCRApprovers) {

            String[] userGroupIdentifier = additionalApprover.split(" ");
            if (userGroupIdentifier[0].equals("group")) {
                String groupName = userGroupIdentifier[1];

                List<AccountInfo> members = null;

                members = gerritApi.groups().id(groupName).members();

                List<String> membersUsernames = members.parallelStream().map(accountInfo -> {
                    return accountInfo.username;
                }).collect(Collectors.toList());
                allUsernames.addAll(membersUsernames);
            } else if (userGroupIdentifier[0].equals("user")) {
                String username = userGroupIdentifier[1];
                allUsernames.add(gerritApi.accounts().id(username).get().username);
            }
        }
        return allUsernames;
    }
}
