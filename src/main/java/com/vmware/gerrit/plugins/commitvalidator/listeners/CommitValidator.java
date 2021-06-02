package com.vmware.gerrit.plugins.commitvalidator.listeners;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.inject.Inject;
import com.vmware.gerrit.plugins.commitvalidator.config.CommitValidatorConfig;
import com.vmware.gerrit.plugins.commitvalidator.entities.*;
import com.vmware.gerrit.plugins.commitvalidator.utils.JiraUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class CommitValidator implements CommitValidationListener {

    @Inject
    protected GerritApi gerritApi;
    @Inject
    private PluginConfigFactory pluginConfigFactory;

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
            throws CommitValidationException {

        // Read patchset values
        String projectName = receiveEvent.project.getName();
        // TODO: find a better way to handle branch names
        String branchName = receiveEvent.getBranchNameKey().branch().replaceFirst("refs/heads/", "");
        String commitMessageBody = receiveEvent.commit.getFullMessage();
        String commit = receiveEvent.commit.getId().toString();

        // Get plugin configuration
        CommitValidatorConfig pluginConfig = getPluginConfig();

        // Fetch Project rules
        ProjectRules projectRules = pluginConfig.getProjectRules(projectName, branchName);

        // Skip the validation if project is not configured with any rules
        if (projectRules == null) {
            log.info(
                    "Project: {}, commit: {} - This project is not configured with any validation rules in the plugin config",
                    projectName, commit);
            return ImmutableList.of();
        }

        // Skip the validation if project is configured but not enabled for validation
        if (!projectRules.isEnabled()) {
            log.info("Project: {}, commit: {} - This project is not enabled for validation in the plugin config",
                    projectName, commit);
            return ImmutableList.of();
        }
        log.debug("Project: {}, commit: {} - Project rules for this project: {}", projectName, commit,
                projectRules.toString());

        // Get commit template for this project
        CommitTemplate commitTemplate = pluginConfig.getCommitTemplate(projectRules.getCommitTemplate());

        // Skip the validation if no commit template is configured for this project or
        // configured template definition is not available in the plugin config
        if (commitTemplate == null) {
            log.info(
                    "Project: {}, commit: {} - Either no commit template is configured for this project or unable to find the configured commit template in the plugin config, commit template: {}",
                    projectName, commit, projectRules.getCommitTemplate());
            return ImmutableList.of();
        }

        // Get mandatory entries from configured template
        List<TemplateEntry> mandatoryTemplateEntries = commitTemplate.getMandatoryEntry();
        log.debug("Project: {}, commit: {} - Template mandatory entries: {}", projectName, commit,
                mandatoryTemplateEntries.toString());

        // Get lines from commit message body
        String[] messageBodyLines = parseCommitMessage(commitMessageBody);

        // Validate whether all template mandatory entries rules are fullfilled by the
        // commit message and collect all validation error entries.
        List<MessageEntry> validationErrors = mandatoryTemplateEntries.parallelStream().filter(entry -> {
            // Ignore the entry check if both key and value are not present in template
            // entry definition
            return !(StringUtils.isEmpty(entry.getKey()) && StringUtils.isEmpty(entry.getValue()));
        }).map(entry -> {
            MessageEntry messageEntry = new MessageEntry();
            messageEntry.setEntryName(entry.getName());
            messageEntry.setEntryType(entry.getType());
            messageEntry.setExample(entry.getExampleValue());

            // Get actual value of the template entry
            String actualValue = getTemplateEntryDataFromCommitMessage(entry, messageBodyLines);

            // If actual value is null, that means the field is missing. Set validation
            // accordingly.
            if (actualValue == null) {
                messageEntry.setEntryValidationStatus(TemplateEntryValidationStatus.MISSING_ENTRY);
                return messageEntry;
            }
            messageEntry.setActualValue(actualValue);

            // Validate the value based on entry type
            TemplateEntryValidationStatus validationStatus = null;
            if (StringUtils.isEmpty(actualValue)) {
                validationStatus = TemplateEntryValidationStatus.MISSING_VALUE;
            } else {
                switch (entry.getType()) {
                    case BOOLEAN:
                        validationStatus = validateBoolEntry(actualValue);
                        break;
                    case INTEGER:
                        validationStatus = validateIntEntry(actualValue);
                        break;
                    case STRING:
                    default:
                        validationStatus = validateStringEntry(entry, actualValue);
                }
            }
            log.info(
                    "Project: {}, commit: {}, template entry name: {}, entry value pattern: {}, entry actual value: {}",
                    projectName, commit, entry.getName(), entry.getValue(), actualValue);
            messageEntry.setEntryValidationStatus(validationStatus);
            return messageEntry;
        }).filter(messageEntry -> {
            // Ignore VALID value entries
            return messageEntry.getEntryValidationStatus() != TemplateEntryValidationStatus.VALID_VALUE;
        }).collect(Collectors.toList());

        // Construct the error message if there are validation errors
        if (!validationErrors.isEmpty()) {
            String errorMessage = getMissingentriesMessage(validationErrors);

            // Throw the validation error. This gets displayed in user's console/screen.
            CommitValidationMessage m = new CommitValidationMessage(errorMessage, true);
            throw new CommitValidationException(Constants.MESSAGE_MISSING_OR_INVALID_ENTRIES, ImmutableList.of(m));
        }

        // No errors. Allow further processing of the change by Gerrit.
        return ImmutableList.of();
    }

    /**
     * Validates boolean entry value
     *
     * @param entryActualValue
     * @return
     */
    private TemplateEntryValidationStatus validateBoolEntry(String entryActualValue) {
        Pattern pattern = Pattern.compile("true|false", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(entryActualValue);
        if (!matcher.find()) {
            return TemplateEntryValidationStatus.INVALID_VALUE;
        }
        return TemplateEntryValidationStatus.VALID_VALUE;
    }

    /**
     * Validates integer entry value
     *
     * @param entryActualValue
     * @return
     */
    private TemplateEntryValidationStatus validateIntEntry(String entryActualValue) {
        try {
            Integer.parseInt(entryActualValue);
        } catch (NumberFormatException e) {
            return TemplateEntryValidationStatus.INVALID_VALUE;
        }
        return TemplateEntryValidationStatus.VALID_VALUE;
    }

    /**
     * Validates string entry value as per entry value pattern
     *
     * @param entry
     * @param entryActualValue
     * @return
     */
    private TemplateEntryValidationStatus validateStringEntry(TemplateEntry entry, String entryActualValue) {
        // Validate the value as per pattern
        Pattern valPattern = Pattern.compile(entry.getValue().trim());
        Matcher valMatcher = valPattern.matcher(entryActualValue.trim());

        if (!valMatcher.matches()) {
            return TemplateEntryValidationStatus.INVALID_VALUE;
        }

        if (entry.getEndpointType() != null && entry.getEndpointType() == EndpointType.JIRA) {
            // Validate against endpoint
            // Get plugin configuration
            CommitValidatorConfig pluginConfig = getPluginConfig();
            JiraEndpoint jiraEndpoint = pluginConfig.getJiraEndpointConfig(entry.getEndpointName());

            JiraUtils jiraUtils = new JiraUtils(jiraEndpoint.getUrl(), jiraEndpoint.getUsername(), jiraEndpoint.getPassword());
            boolean isJiraValid = jiraUtils.isIssueIdValid(entryActualValue);

            log.info(
                    "Project: {}, commit: {}, template entry name: {}, is Jira valid : {}", "-", "-", entry.getName(), isJiraValid);

            if (!isJiraValid) {
                return TemplateEntryValidationStatus.INVALID_VALUE;
            }
        }
        return TemplateEntryValidationStatus.VALID_VALUE;
    }

    /**
     * Builds the error message when mandatory template entries are missing
     *
     * @return
     */
    private String getMissingentriesMessage(List<MessageEntry> validationMessageEntries) {
        Message validationMsg = new Message(validationMessageEntries);

        return String.format("%n%s%n\tINVALID COMMIT MESSAGE\t%n%s%n%s%n%s%n%n%s%n%n%s", Constants.LINE_BREAK_ASTERISK,
                Constants.LINE_BREAK_ASTERISK, Constants.MESSAGE_MISSING_OR_INVALID_ENTRIES,
                Constants.LINE_BREAK_HYPHEN, validationMsg.toString(), Constants.LINE_BREAK_ASTERISK);
    }

    /**
     * Gets the given template entry value if it is present. Else null is returned.
     *
     * @param entry
     * @param commitMessageLines
     * @return
     */
    private String getTemplateEntryDataFromCommitMessage(TemplateEntry entry, String[] commitMessageLines) {
        return Arrays.stream(commitMessageLines).filter(message -> {
            return message.trim().startsWith(entry.getName());
        }).map(message -> {
            String[] fieldParts = message.split(":");
            return fieldParts[1].trim();
        }).findFirst().orElse(null);
    }

    /**
     * Splits commit message into lines
     *
     * @param commitMessage
     * @return
     */
    private String[] parseCommitMessage(String commitMessage) {
        return commitMessage.split(System.getProperty("line.separator"));
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