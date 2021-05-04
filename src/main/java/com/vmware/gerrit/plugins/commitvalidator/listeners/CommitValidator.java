package com.vmware.gerrit.plugins.commitvalidator.listeners;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.inject.Inject;
import com.vmware.gerrit.plugins.commitvalidator.config.CommitValidatorConfig;
import com.vmware.gerrit.plugins.commitvalidator.entities.CommitTemplate;
import com.vmware.gerrit.plugins.commitvalidator.entities.TemplateEntry;
import com.vmware.gerrit.plugins.commitvalidator.entities.TemplateEntryType;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommitValidator implements CommitValidationListener {
    private static final Logger log = LoggerFactory.getLogger(CommitValidator.class);

    @Inject
    private PluginConfigFactory pluginConfigFactory;

    @Inject
    protected GerritApi gerritApi;

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
            throws CommitValidationException {

        String projectName = receiveEvent.project.getName();
        String branchName = receiveEvent.getBranchNameKey().branch().replaceFirst("refs/heads/", "");
        String commitMessageBody = receiveEvent.commit.getFullMessage();
        String commit = receiveEvent.commit.getId().toString();

        CommitValidatorConfig pluginConfig = getPluginConfig();

        // Check if the project of this commit is configured for validation
        String templateForThisProject = pluginConfig.getTemplateForProject(projectName, branchName);
        if (templateForThisProject == null) {
            log.info("Project: {}, commit: {} - This project is not configured with any validation template",
                    projectName, commit);

            // Skip validation
            return ImmutableList.of();
        }
        log.debug("Project: {}, commit: {} - Matching validation template for this project: {}", projectName, commit,
                templateForThisProject);

        // Get commit template for this project
        CommitTemplate commitTemplate = pluginConfig.getCommitTemplate(templateForThisProject);
        if (commitTemplate == null) {
            log.debug("Project: {}, commit: {} - Unable to find commit template: {}", projectName, commit,
                    templateForThisProject);

            // Skip validation
            ImmutableList.of();
        }

        List<TemplateEntry> commitTemplateEntries = commitTemplate.getMandatoryEntry();
        log.debug("Project: {}, commit: {} - Template entries: {}", projectName, commit,
                commitTemplateEntries.toString());

        // Validate whether all fields are present in the commit message
        List<TemplateEntry> missingMandatoryFields = commitTemplateEntries.stream().filter(entry -> {
            // Check whether both entry key and value are not present
            if (StringUtils.isEmpty(entry.getKey()) && StringUtils.isEmpty(entry.getValue())) {
                // Ignore the entry as both key and value are not present
                return false;
            }

            // Check whether key exists in the commitMessageBody
            if (!StringUtils.isEmpty(entry.getKey()) && !commitMessageBody.contains(entry.getKey())) {
                // Missing entry
                return true;
            }

            // Validate the value based on entry type
            String entryValue = entry.getValue();

            // Set entry type to String if not provided
            TemplateEntryType type = null;
            if (entry.getType() == null) {
                type = TemplateEntryType.STRING;
            }
            switch (type) {
                case BOOLEAN:
                    Pattern pattern = Pattern.compile("true|false", Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(entryValue);
                    return !matcher.matches();

                case INTEGER:
                    try {
                        int intValue = Integer.parseInt(entryValue);
                        return false;
                    } catch (NumberFormatException e) {
                        return true;
                    }
                case STRING:
                default:
                    return StringUtils.isEmpty(entryValue);
            }
        }).collect(Collectors.toList());

        if (missingMandatoryFields.size() > 0) {
            String errorMessage = getMissingFieldsMessage(missingMandatoryFields);
            CommitValidationMessage m = new CommitValidationMessage(errorMessage, true);
            throw new CommitValidationException("Missing or invalid commit message fields", ImmutableList.of(m));
        }

        return ImmutableList.of();
    }

    /**
     * Builds the error message when mandatory template fields are missing
     * 
     * @return
     */
    private String getMissingFieldsMessage(List<TemplateEntry> templateEntries) {
        String messageBreak = "\n************************************************\n";

        String missingFileds = templateEntries.stream().map(entry -> {
            String expectedValue = entry.getValue();
            switch (entry.getType()) {
                case BOOLEAN:
                    expectedValue = "true/false";
                    break;
                case INTEGER:
                    expectedValue = "...,-1,0,1,...etc";
                    break;
            }
            return String.format("%nField: %s, Type: %s, Expected: %s, Actual: %s", entry.getName(), entry.getType(),
                    expectedValue, " - ");

        }).collect(Collectors.joining(","));

        return String.format("%sMissing or invalid commit message fields:%n%s%s", messageBreak, missingFileds,
                messageBreak);
    }

    /**
     * Gets plugins configuration from etc/commit-validator.config file
     *
     * @return
     */
    private CommitValidatorConfig getPluginConfig() {
        return new CommitValidatorConfig(pluginConfigFactory);
    }
}