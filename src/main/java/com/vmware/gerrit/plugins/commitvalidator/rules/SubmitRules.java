package com.vmware.gerrit.plugins.commitvalidator.rules;

import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitRecord.Status;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.Inject;
import com.vmware.gerrit.plugins.commitvalidator.config.CommitValidatorConfig;
import com.vmware.gerrit.plugins.commitvalidator.entities.ProjectRules;
import com.vmware.gerrit.plugins.commitvalidator.utils.GerritUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class SubmitRules implements SubmitRule {
    @Inject
    protected GerritApi gerritApi;
    @Inject
    private PluginConfigFactory pluginConfigFactory;

    public Optional<SubmitRecord> evaluate(ChangeData changeData) {
        String projectName = changeData.project().get();
        // TODO: find a better way to handle branch names
        String branchName = changeData.change().getDest().branch().replaceFirst("refs/heads/", "");
        String commit = changeData.change().getId().toString();
        GerritUtils gerritUtils = new GerritUtils(gerritApi);

        // Get plugin configuration
        CommitValidatorConfig pluginConfig = new CommitValidatorConfig(pluginConfigFactory);

        // Fetch Project rules
        ProjectRules projectRules = pluginConfig.getProjectRules(projectName,
                branchName);

        // Skip the voting if project is not configured with any rules
        if (projectRules == null) {
            log.info(
                    "Project: {}, commit: {} - This project is not configured with any validation rules in the plugin config",
                    projectName, commit);
            return Optional.empty();
        }

        // Skip the voting if project is configured but not enabled for validation
        if (!projectRules.isEnabled()) {
            log.info("Project: {}, commit: {} - This project is not enabled for validation in the plugin config",
                    projectName, commit);
            return Optional.empty();
        }

        // Skip the voting if project is configured to skip validation for this committer
        if (!projectRules.getSkipTemplateValidationFor().isEmpty()) {
            try {
                List<String> skipValidationUsers = gerritUtils.getAllUsers(projectRules.getSkipTemplateValidationFor());
                boolean skipValidation = skipValidationUsers.contains(changeData.getCommitter().getName());

                if(skipValidation) {
                    log.info("Project: {}, commit: {}, committer: {} - Skipping validation for this commit as Committer is in skip list in the plugin config",
                            projectName, commit, changeData.getCommitter().getName());
                    return Optional.empty();
                }
            } catch (RestApiException e) {
                // TODO: handle this case
            }
        }

        if (projectRules.getAdditionalCodeReviewApprovalConditions().isEmpty()) {
            log.info(
                    "Project: {}, commit: {} - This project is not configured with any additional approvers conditions",
                    projectName, commit);
            return Optional.empty();
        }

        List<String> additionalApprovalConditions = projectRules.getAdditionalCodeReviewApprovalConditions();

        // Validate additional approvers conditions
        try {
            List<String> allAdditionalApprovers = gerritUtils.getAllUsers(projectRules.getAdditionalCodeReviewApprovers());

            log.info("Project: {}, commit: {} - all additional Approvers {}", projectName, commit, allAdditionalApprovers);

            List<String> currentCRApprovers = changeData.
                    currentApprovals().
                    parallelStream().
                    filter(patchSetApproval -> {
                        return patchSetApproval.labelId().get().equals("Code-Review");
                    }).map(patchSetApproval -> {
                return patchSetApproval.accountId().get();
            }).map(accountId -> {
                try {
                    return gerritApi.accounts().id(accountId).get().username;
                } catch (RestApiException e) {
                    // TODO: handle this case
                }
                return null;
            }).filter(username -> {
                return username != null;
            }).collect(Collectors.toList());

            log.info("Project: {}, commit: {} - current Approvers {}", projectName, commit, currentCRApprovers);

            boolean additionalApprovalDone = currentCRApprovers.stream().anyMatch(element -> allAdditionalApprovers.contains(element));
            log.info("Project: {}, commit: {} - additionalApprovalDone {}", projectName, commit, additionalApprovalDone);

            // Vote OK if at least one additional approval is done
            if (additionalApprovalDone) {
                return vote(Status.OK);
            } else {
                return vote(Status.NOT_READY);
            }

        } catch (RestApiException e) {
            // TODO: handle this case
        }

        // Vote OK
        return vote(Status.OK);
    }


    /**
     * Votes for the change
     *
     * @return
     */
    private Optional<SubmitRecord> vote(Status status) {
        SubmitRecord record = new SubmitRecord();
        record.status = status;
        return Optional.of(record);
    }
}