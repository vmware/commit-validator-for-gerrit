package com.vmware.gerrit.plugins.commitvalidator.entities;

public class Constants {
    private Constants() {
        throw new IllegalStateException("Constants class");
    }

    // Config constants
    public static final String CONFIG_FILENAME_WITHOUT_EXTN = "commit-validator";
    public static final String CONFIG_SECTION_JIRA_ENDPOINT = "endpoint-jira";
    public static final String CONFIG_SECTION_TEMPLATE_ENTRY = "template-entry";
    public static final String CONFIG_SECTION_COMMIT_TEMPLATE = "commit-template";
    public static final String CONFIG_SECTION_PROJECT_RULES = "project-rules";
    public static final String CONFIG_PROJECT_RULES_BRANCH = "branch";
    public static final String CONFIG_PROJECT_RULES_COMMIT_TEMPLATE = "commitTemplate";
    public static final String CONFIG_PROJECT_RULES_SKIP_TEMPLATE_VALIDATION = "skipTemplateValidationFor";
    public static final String CONFIG_PROJECT_RULES_ADDITIONAL_CR_APPROVAL_IF = "requiresAdditionalCodeReviewApprovalIf";
    public static final String CONFIG_PROJECT_RULES_ADDITIONAL_CR_APPROVERS = "additionalCodeReviewApprovers";
    public static final String CONFIG_ENDPOINT_URL = "url";
    public static final String CONFIG_ENDPOINT_USERNAME = "username";
    public static final String CONFIG_ENDPOINT_PASSWORD = "password";
    public static final String CONFIG_ENABLED = "enabled";
    public static final String CONFIG_TEMPLATE_ENTRY_KEY = "key";
    public static final String CONFIG_TEMPLATE_ENTRY_VALUE = "value";
    public static final String CONFIG_TEMPLATE_ENTRY_SAMPLE_VALUE = "sampleValue";
    public static final String CONFIG_TEMPLATE_ENTRY_TYPE = "type";
    public static final String CONFIG_TEMPLATE_ENTRY_VALIDATE_AGAINST_ENDPOINT = "validateAgainstEndpoint";
    public static final String CONFIG_TEMPLATE_ENTRY_ENDPOINT_NAME = "endpointName";
    public static final String CONFIG_TEMPLATE_ENTRY_ENDPOINT_TYPE = "endpointType";
    public static final String CONFIG_COMMIT_TEMPLATE_MANDATORY_ENTRY = "mandatoryEntry";
    public static final String CONFIG_COMMIT_TEMPLATE_OPTIONAL_ENTRY = "optionalEntry";
    public static final String CONFIG_PROJECT = "project";

    // Message Constants
    public static final String LINE_BREAK_ASTERISK = "************************************************************";
    public static final String LINE_BREAK_HYPHEN = "------------------------------------------------------------";
    public static final String MESSAGE_MISSING_OR_INVALID_ENTRIES = "Following entries are either missing or have invalid values";
}
