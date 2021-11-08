package com.vmware.gerrit.plugins.commitvalidator.listeners;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.inject.Inject;
import com.vmware.gerrit.plugins.commitvalidator.config.CommitValidatorConfig;
import com.vmware.gerrit.plugins.commitvalidator.entities.*;
import com.vmware.gerrit.plugins.commitvalidator.utils.GerritUtils;
import com.vmware.gerrit.plugins.commitvalidator.utils.JiraUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
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
        String commitSubject = receiveEvent.commit.getShortMessage();
        String commit = receiveEvent.commit.getId().toString();
        String committer = receiveEvent.commit.getCommitterIdent().getEmailAddress().split("@")[0];
        String author = receiveEvent.commit.getAuthorIdent().getEmailAddress().split("@")[0];

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

        // Get commit template for this project
        CommitTemplate commitTemplate = pluginConfig.getCommitTemplate(projectRules.getCommitTemplate());

        // Skip the validation if no commit template is configured for this project or
        // configured template definition is not available in the plugin config
        if (commitTemplate == null) {
            log.info(
                    "Project: {}, commit: {} - Either no commit template is configured for this project or unable to find the configured one in the plugin config, commit template: {}",
                    projectName, commit, projectRules.getCommitTemplate());
            return ImmutableList.of();
        }

        // Skip the validation if project is configured to skip validation for this author/committer
        GerritUtils gerritUtils = new GerritUtils(gerritApi);
        try {
            // For Author
            if (!projectRules.getSkipTemplateValidationForAuthors().isEmpty()) {
                List<String> skipValidationUsers = gerritUtils.getAllUsers(projectRules.getSkipTemplateValidationForAuthors());
                log.info("Project: {}, commit: {}, author: {} - skip eligible users {}", projectName, commit, author, skipValidationUsers.toString());
                boolean skipValidation = skipValidationUsers.contains(author);

                if (skipValidation) {
                    log.info("Project: {}, commit: {}, author: {} - Skipping validation for this commit as Author is in skip list in the plugin config",
                            projectName, commit, author);
                    return ImmutableList.of();
                }
            }

            // For Committer
            if (!projectRules.getSkipTemplateValidationForCommitters().isEmpty()) {
                List<String> skipValidationUsers = gerritUtils.getAllUsers(projectRules.getSkipTemplateValidationForCommitters());
                log.info("Project: {}, commit: {}, committer: {} - skip eligible users {}", projectName, commit, committer, skipValidationUsers.toString());
                boolean skipValidation = skipValidationUsers.contains(committer);


                if (skipValidation) {
                    log.info("Project: {}, commit: {}, committer: {} - Skipping validation for this commit as Committer is in skip list in the plugin config",
                            projectName, commit, committer);
                    return ImmutableList.of();
                }

            }
        } catch (RestApiException e) {
            // TODO: handle this case
        }

        // Get mandatory entries from configured template
        List<TemplateEntry> mandatoryTemplateEntries = commitTemplate.getMandatoryEntry();

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
            if (entry.getKind() == TemplateEntryKind.KEY_VAL) {
                messageEntry.setEntryName(entry.getKey());
            } else {
                messageEntry.setEntryName(entry.getName());
            }

            messageEntry.setKind(entry.getKind());
            messageEntry.setEntryType(entry.getType());
            messageEntry.setExample(entry.getExampleValue());

            // Extract the values for the template entry base on its kind
            TemplateEntryKind entryKind = entry.getKind();

            if (entryKind == TemplateEntryKind.KEY_VAL) {
                // Extract value from matching key-value pair
                String keyValue = extractValueFromMatchingKeyValPair(messageBodyLines, entry.getKey());

                // If no key is found, return with missing entry message
                if (keyValue == null) {
                    messageEntry.setEntryValidationStatus(TemplateEntryValidationStatus.MISSING_KEY);
                    return messageEntry;
                } else if (keyValue.isEmpty()) {
                    messageEntry.setEntryValidationStatus(TemplateEntryValidationStatus.MISSING_VALUE);
                    return messageEntry;
                }

                // Set actual value to message
                messageEntry.setActualValues(Arrays.asList(keyValue));

                // Validate the value and set to message
                TemplateEntryValidationStatus validationStatus = validateKeyValPairEntryValue(entry, keyValue);
                messageEntry.setEntryValidationStatus(validationStatus);
            } else {
                // Extract matching values
                List<String> matchingValues = new ArrayList<>();
                if (entryKind == TemplateEntryKind.STR_SUB) {
                    matchingValues.addAll(extractMatchingStrings(commitSubject, entry.getValue()));
                } else if (entryKind == TemplateEntryKind.STR_BODY) {
                    matchingValues.addAll(extractMatchingStrings(commitMessageBody, entry.getValue()));
                }

                // Return if no matching values are found
                if (matchingValues.isEmpty()) {
                    messageEntry.setEntryValidationStatus(TemplateEntryValidationStatus.MISSING_VALUE);
                    return messageEntry;
                }

                // Set actual value to message
                messageEntry.setActualValues(matchingValues);

                log.info(
                        "Project: {}, commit: {}, matching values:{}", projectName, commit, matchingValues);

                // Validate all matching values and extract valid values
                List<String> validValuesFromMatched = matchingValues.stream().filter(s -> {
                    log.info(
                            "Project: {}, commit: {}, validating the value:{}", projectName, commit, s);
                    TemplateEntryValidationStatus validationStatus = validateStringEntry(entry, s);
                    return validationStatus == TemplateEntryValidationStatus.VALID_VALUE;
                }).collect(Collectors.toList());

                // Set the validation status of entry as VALID if at least
                // one value is valid
                if (validValuesFromMatched.size() > 0) {
                    messageEntry.setEntryValidationStatus(TemplateEntryValidationStatus.VALID_VALUE);
                } else {
                    messageEntry.setEntryValidationStatus(TemplateEntryValidationStatus.INVALID_VALUE);
                }
            }
            log.info(
                    "Project: {}, commit: {}, template entry name: {}, entry value pattern: {}, entry actual value: {}",
                    projectName, commit, entry.getName(), entry.getValue(), messageEntry.getActualValues());
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
            throw new CommitValidationException(Constants.MESSAGE_VALIDATION_EXCEPTION, ImmutableList.of(m));
        }

        // No errors. Allow further processing of the change by Gerrit.
        return ImmutableList.of();
    }

    /**
     * Validates the key value pair value based on its type
     *
     * @param entry
     * @param keyValue
     * @return
     */
    private TemplateEntryValidationStatus validateKeyValPairEntryValue(TemplateEntry entry, String keyValue) {
        switch (entry.getType()) {
            case BOOLEAN:
                return validateBoolEntry(keyValue);
            case INTEGER:
                return validateIntEntry(keyValue);
            case STRING:
            default:
                return validateStringEntry(entry, keyValue);
        }
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

        log.info(">>validate value against endpoint {}", entry.isValidateValueAgainstEndpoint());

        // Validate against Endpoint if set
        if (entry.isValidateValueAgainstEndpoint()) {
            if (entry.getEndpointType() == null || StringUtils.isEmpty(entry.getEndpointName())) {
                log.warn("Unable to validate the value of template entry {} against endpoint as endpoint details are missing", entry.getName());
                return TemplateEntryValidationStatus.VALID_VALUE;
            }

            log.info(">>validating based on type");
            // Validate based on endpoint type
            switch (entry.getEndpointType()) {
                case JIRA:
                    log.info(">>validating based against Jira");
                    return validateAgainstJira(entry, entryActualValue);
                default:
                    log.warn("Unable to validate the value of template entry {} against endpoint as endpoint type {} is unknown", entry.getName(), entry.getEndpointType());
                    // Return as Valid as we do not know what to validate here
                    return TemplateEntryValidationStatus.VALID_VALUE;
            }
        }
        return TemplateEntryValidationStatus.VALID_VALUE;
    }

    /**
     * Validates the given value against endpoint
     *
     * @param entry
     * @param value
     * @return
     */
    private TemplateEntryValidationStatus validateAgainstJira(TemplateEntry entry, String value) {
        // Remove any unwanted braces from Jira issue ID
        // In general this is not needed but to handle VMware use cases, this is added.
        String actualValue = value.replaceAll("[\\[\\]]", "");

        // Get plugin configuration
        CommitValidatorConfig pluginConfig = getPluginConfig();
        JiraEndpoint jiraEndpoint = pluginConfig.getJiraEndpointConfig(entry.getEndpointName());
        JiraUtils jiraUtils = new JiraUtils(jiraEndpoint.getUrl(), jiraEndpoint.getUsername(), jiraEndpoint.getPassword());
        boolean isJiraValid = jiraUtils.isIssueIdValid(actualValue, entry.getRejectedStatus());

        log.info(">> Jira {} valid? {}", actualValue, isJiraValid);
        if (!isJiraValid) {
            return TemplateEntryValidationStatus.INVALID_VALUE;
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
     * Extracts the value for given key if matching key-val pair is found in commit message body.
     * If no value is found for give key, an empty string is returned.
     * And if no matching key itself is not found, null is returned.
     *
     * @param commitMessageLines
     * @param key
     * @return
     */
    private String extractValueFromMatchingKeyValPair(String[] commitMessageLines, String key) {
        return Arrays.stream(commitMessageLines).filter(message -> {
            return message.trim().startsWith(key);
        }).map(message -> {
            String[] keyValPair = message.split(":");

            if (keyValPair.length <= 1) {
                // No value is available for given key
                // Return empty string
                return "";
            }
            return keyValPair[1].trim();
        }).findFirst().orElse(null);
    }

    /**
     * Extracts the matching string from given text
     *
     * @param inputStr
     * @param matchPattern
     * @return
     */
    private List<String> extractMatchingStrings(String inputStr, String matchPattern) {
        Pattern pattern = Pattern.compile(matchPattern.trim());
        Matcher matcher = pattern.matcher(inputStr.trim());

        List<String> matchingStrs = new ArrayList<>();
        while (matcher.find()) {
            matchingStrs.add(matcher.group());
        }
        return matchingStrs;
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