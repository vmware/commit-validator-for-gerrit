package com.vmware.gerrit.plugins.commitvalidator.listeners;

import java.util.Arrays;
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
import com.vmware.gerrit.plugins.commitvalidator.entities.Constants;
import com.vmware.gerrit.plugins.commitvalidator.entities.Message;
import com.vmware.gerrit.plugins.commitvalidator.entities.MessageEntry;
import com.vmware.gerrit.plugins.commitvalidator.entities.ProjectRules;
import com.vmware.gerrit.plugins.commitvalidator.entities.TemplateEntry;
import com.vmware.gerrit.plugins.commitvalidator.entities.TemplateEntryValidationStatus;

import org.apache.commons.lang.StringUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommitValidator implements CommitValidationListener {

    @Inject
    private PluginConfigFactory pluginConfigFactory;

    @Inject
    protected GerritApi gerritApi;

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
            throws CommitValidationException {

        // Read patchset values
        String projectName = receiveEvent.project.getName();
        String branchName = receiveEvent.getBranchNameKey().branch().replaceFirst("refs/heads/", "");
        String commitMessageBody = receiveEvent.commit.getFullMessage();
        String commit = receiveEvent.commit.getId().toString();

        // Get plugin configuration
        // TODO: read once and keep it in memory so that
        // the processing is faster. For any config file changes, we
        // can reload the plugin with gerrit reload command
        CommitValidatorConfig pluginConfig = getPluginConfig();

        // Check if the project of this commit is configured for validation
        ProjectRules projectRules = pluginConfig.getProjectRules(projectName, branchName);
        if (projectRules == null) {
            log.info("Project: {}, commit: {} - This project is not configured with any validation rules", projectName,
                    commit);

            // Skip validation
            return ImmutableList.of();
        }

        if (!projectRules.isEnabled()) {
            log.info("Project: {}, commit: {} - This project is not enabled for validation", projectName, commit);

            // Skip validation
            return ImmutableList.of();
        }

        log.info("Project: {}, commit: {} - Validation rules for this project: {}", projectName, commit,
                projectRules.toString());

        // Get commit template for this project
        CommitTemplate commitTemplate = pluginConfig.getCommitTemplate(projectRules.getCommitTemplate());
        if (commitTemplate == null) {
            log.info("Project: {}, commit: {} - Unable to find commit template: {}", projectName, commit,
                    projectRules.getCommitTemplate());

            // Skip validation
            return ImmutableList.of();
        }

        // Get mandatory entries from configured template
        List<TemplateEntry> mandatoryTemplateEntries = commitTemplate.getMandatoryEntry();
        log.info("Project: {}, commit: {} - Template entries: {}", projectName, commit,
                mandatoryTemplateEntries.toString());

        // Get lines from commit message body
        String[] messageBodyLines = parseCommitMessage(commitMessageBody);

        // Validate whether all template mandatory entries rules
        // are fullfilled by the commit message
        List<MessageEntry> validationMessageEntries = mandatoryTemplateEntries.parallelStream().filter(entry -> {
            // Check whether both entry key and value are not present in template
            return !(StringUtils.isEmpty(entry.getKey()) && StringUtils.isEmpty(entry.getValue()));
        }).map(entry -> {
            MessageEntry messageEntry = new MessageEntry();
            messageEntry.setEntryName(entry.getName());
            messageEntry.setEntryType(entry.getType());
            messageEntry.setExample(entry.getExampleValue());

            // Get actual value of the template entry
            String actualValue = getTemplateEntryDataFromCommitMessage(entry, messageBodyLines);

            // If actual value is null, that means the field is missing.
            // Set validation accordingly.
            if (actualValue == null) {
                messageEntry.setEntryValidationStatus(TemplateEntryValidationStatus.MISSING_ENTRY);
                return messageEntry;
            }

            messageEntry.setActualValue(actualValue);

            // Validate the value based on entry type
            switch (entry.getType()) {
                case BOOLEAN:
                    Pattern pattern = Pattern.compile("true|false", Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(actualValue);
                    if (!matcher.find()) {
                        messageEntry.setEntryValidationStatus(TemplateEntryValidationStatus.INVALID_VALUE);
                        return messageEntry;
                    }
                    break;
                case INTEGER:
                    try {
                        int intValue = Integer.parseInt(actualValue);
                    } catch (NumberFormatException e) {
                        messageEntry.setEntryValidationStatus(TemplateEntryValidationStatus.INVALID_VALUE);
                        return messageEntry;
                    }
                    break;
                case STRING:
                default:
                    if (StringUtils.isEmpty(actualValue)) {
                        messageEntry.setEntryValidationStatus(TemplateEntryValidationStatus.MISSING_VALUE);
                        return messageEntry;
                    }

                    Pattern valPattern = Pattern.compile(entry.getValue().trim());
                    Matcher valMatcher = valPattern.matcher(actualValue.trim());
                    log.info(
                            "Project: {}, commit: {}, template entry name: {}, entry value pattern: {}, entry actual value: {}, matches: {}",
                            projectName, commit, entry.getName(), entry.getValue(), actualValue, valMatcher.matches());
                    if (!valMatcher.matches()) {
                        messageEntry.setEntryValidationStatus(TemplateEntryValidationStatus.INVALID_VALUE);
                        return messageEntry;
                    }
            }
            messageEntry.setEntryValidationStatus(TemplateEntryValidationStatus.VALID_VALUE);
            return messageEntry;
        }).filter(messageEntry -> {
            // Ignore VALID value entries
            return messageEntry.getEntryValidationStatus() != TemplateEntryValidationStatus.VALID_VALUE;
        }).collect(Collectors.toList());

        if (!validationMessageEntries.isEmpty()) {
            // Construct error message
            String errorMessage = getMissingentriesMessage(validationMessageEntries);

            // Throw
            CommitValidationMessage m = new CommitValidationMessage(errorMessage, true);
            throw new CommitValidationException(Constants.MESSAGE_MISSING_OR_INVALID_ENTRIES, ImmutableList.of(m));
        }
        return ImmutableList.of();
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