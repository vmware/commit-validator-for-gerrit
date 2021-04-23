package com.vmware.gerrit.plugins.commitvalidator.config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gerrit.server.config.PluginConfigFactory;
import com.vmware.gerrit.plugins.commitvalidator.entities.CommitTemplate;
import com.vmware.gerrit.plugins.commitvalidator.entities.EndpointType;
import com.vmware.gerrit.plugins.commitvalidator.entities.JiraEndpoint;
import com.vmware.gerrit.plugins.commitvalidator.entities.TemplateEntry;
import com.vmware.gerrit.plugins.commitvalidator.entities.TemplateEntryType;

import org.apache.commons.lang3.EnumUtils;

public class CommitValidatorConfig {
        public static final String CONFIG_FILENAME_WITHOUT_EXTN = "commit-validator";
        public static final String CONFIG_SECTION_JIRA_ENDPOINT = "endpoint-jira";
        public static final String CONFIG_SECTION_TEMPLATE_ENTRY = "template-entry";
        public static final String CONFIG_SECTION_COMMIT_TEMPLATE = "commit-template";
        public static final String CONFIG_SECTION_PROJECT_MAPPINGS = "project-mappings";
        public static final String CONFIG_ENDPOINT_URL = "url";
        public static final String CONFIG_ENDPOINT_USERNAME = "username";
        public static final String CONFIG_ENDPOINT_PASSWORD = "password";
        public static final String CONFIG_ENABLED = "enabled";
        public static final String CONFIG_TEMPLATE_ENTRY_KEY = "key";
        public static final String CONFIG_TEMPLATE_ENTRY_VALUE = "value";
        public static final String CONFIG_TEMPLATE_ENTRY_TYPE = "type";
        public static final String CONFIG_TEMPLATE_ENTRY_VALIDATE_AGAINST_ENDPOINT = "validateAgainstEndpoint";
        public static final String CONFIG_TEMPLATE_ENTRY_ENDPOINT_NAME = "endpointName";
        public static final String CONFIG_TEMPLATE_ENTRY_ENDPOINT_TYPE = "endpointType";
        public static final String CONFIG_COMMIT_TEMPLATE_MANDATORY_ENTRY = "mandatoryEntry";
        public static final String CONFIG_COMMIT_TEMPLATE_OPTIONAL_ENTRY = "optionalEntry";
        public static final String CONFIG_PROJECT = "project";
        private PluginConfigFactory cfg;

        public CommitValidatorConfig(PluginConfigFactory cfg) {
                this.cfg = cfg;
        }

        /**
         * Fetches the Jira endpoint configuration by name
         * 
         * @param endpointName
         * @return
         */
        public JiraEndpoint getJiraEndpointConfig(String endpointName) {
                // Check whether endpoint exists
                Set<String> endpointKeys = cfg.getGlobalPluginConfig(CONFIG_FILENAME_WITHOUT_EXTN)
                                .getNames(CONFIG_SECTION_JIRA_ENDPOINT, endpointName);

                if (endpointKeys.isEmpty()) {
                        return null; // Endpoint doesn't exist
                }

                // Read Jira endpoint values
                String serverUrl = cfg.getGlobalPluginConfig(CONFIG_FILENAME_WITHOUT_EXTN)
                                .getString(CONFIG_SECTION_JIRA_ENDPOINT, endpointName, CONFIG_ENDPOINT_URL);
                String username = cfg.getGlobalPluginConfig(CONFIG_FILENAME_WITHOUT_EXTN)
                                .getString(CONFIG_SECTION_JIRA_ENDPOINT, endpointName, CONFIG_ENDPOINT_USERNAME);
                String password = cfg.getGlobalPluginConfig(CONFIG_FILENAME_WITHOUT_EXTN)
                                .getString(CONFIG_SECTION_JIRA_ENDPOINT, endpointName, CONFIG_ENDPOINT_PASSWORD);
                return new JiraEndpoint(serverUrl, username, password);
        }

        /**
         * Fetches commit template details by name
         * 
         * @param templateName
         * @return
         */
        public CommitTemplate getCommitTemplate(String templateName) {
                // Check whether template exists
                Set<String> templateKeys = cfg.getGlobalPluginConfig(CONFIG_FILENAME_WITHOUT_EXTN)
                                .getNames(CONFIG_SECTION_COMMIT_TEMPLATE, templateName);

                if (templateKeys.isEmpty()) {
                        return null; // Template doesn't exist
                }

                // Read template values
                String[] mandatoryTemplateEntriesList = cfg.getGlobalPluginConfig(CONFIG_FILENAME_WITHOUT_EXTN)
                                .getStringList(CONFIG_SECTION_COMMIT_TEMPLATE, templateName,
                                                CONFIG_COMMIT_TEMPLATE_MANDATORY_ENTRY);
                String[] optionalTemplateEntriesList = cfg.getGlobalPluginConfig(CONFIG_FILENAME_WITHOUT_EXTN)
                                .getStringList(CONFIG_SECTION_COMMIT_TEMPLATE, templateName,
                                                CONFIG_COMMIT_TEMPLATE_OPTIONAL_ENTRY);

                // Fetch all template entries
                List<TemplateEntry> mandatoryTemplateFields = Arrays.asList(mandatoryTemplateEntriesList).stream()
                                .map(entryName -> getTemplateEntry(entryName)).collect(Collectors.toList());
                List<TemplateEntry> optionalTemplateFields = Arrays.asList(optionalTemplateEntriesList).stream()
                                .map(entryName -> getTemplateEntry(entryName)).collect(Collectors.toList());
                return new CommitTemplate(mandatoryTemplateFields, optionalTemplateFields);
        }

        /**
         * Fetches template entry details by name
         * 
         * @param entryName
         * @return
         */
        public TemplateEntry getTemplateEntry(String entryName) {
                // Check whether template entry exists
                Set<String> templateEntryKeys = cfg.getGlobalPluginConfig(CONFIG_FILENAME_WITHOUT_EXTN)
                                .getNames(CONFIG_SECTION_TEMPLATE_ENTRY, entryName);

                if (templateEntryKeys.isEmpty()) {
                        return null; // Template entry doesn't exist
                }
                // Read template entry values
                String key = cfg.getGlobalPluginConfig(CONFIG_FILENAME_WITHOUT_EXTN)
                                .getString(CONFIG_SECTION_TEMPLATE_ENTRY, entryName, CONFIG_TEMPLATE_ENTRY_KEY);
                String value = cfg.getGlobalPluginConfig(CONFIG_FILENAME_WITHOUT_EXTN)
                                .getString(CONFIG_SECTION_TEMPLATE_ENTRY, entryName, CONFIG_TEMPLATE_ENTRY_VALUE);
                String templateEntryTypeStr = cfg.getGlobalPluginConfig(CONFIG_FILENAME_WITHOUT_EXTN)
                                .getString(CONFIG_SECTION_TEMPLATE_ENTRY, entryName, CONFIG_TEMPLATE_ENTRY_TYPE);
                TemplateEntryType type = null;
                if (EnumUtils.isValidEnumIgnoreCase(TemplateEntryType.class, templateEntryTypeStr)) {
                        type = EnumUtils.getEnum(TemplateEntryType.class, templateEntryTypeStr);
                }
                boolean validateAgainstEndpoint = cfg.getGlobalPluginConfig(CONFIG_FILENAME_WITHOUT_EXTN).getBoolean(
                                CONFIG_SECTION_TEMPLATE_ENTRY, entryName,
                                CONFIG_TEMPLATE_ENTRY_VALIDATE_AGAINST_ENDPOINT, false);
                String endpointTypeStr = cfg.getGlobalPluginConfig(CONFIG_FILENAME_WITHOUT_EXTN).getString(
                                CONFIG_SECTION_TEMPLATE_ENTRY, entryName, CONFIG_TEMPLATE_ENTRY_ENDPOINT_TYPE);
                EndpointType endpointType = null;
                if (EnumUtils.isValidEnumIgnoreCase(EndpointType.class, endpointTypeStr)) {
                        endpointType = EnumUtils.getEnum(EndpointType.class, endpointTypeStr);
                }
                String endpointName = cfg.getGlobalPluginConfig(CONFIG_FILENAME_WITHOUT_EXTN).getString(
                                CONFIG_SECTION_TEMPLATE_ENTRY, entryName, CONFIG_TEMPLATE_ENTRY_ENDPOINT_NAME);

                return new TemplateEntry(key, value, type, validateAgainstEndpoint, endpointType, endpointName);
        }

        /**
         * Fetches configured template name for given project and branch
         * 
         * @param projectName
         * @param branchName
         * @return
         */
        public String getTemplateForProject(String projectName, String branchName) {
                // Fetch all project mappings sub-sections
                Set<String> subSections = cfg.getGlobalPluginConfig(CONFIG_FILENAME_WITHOUT_EXTN)
                                .getSubsections(CONFIG_SECTION_PROJECT_MAPPINGS);

                // Looks for the project in all mappings
                return subSections.stream().filter(subSection -> {
                        String[] projectsList = cfg.getGlobalPluginConfig(CONFIG_FILENAME_WITHOUT_EXTN)
                                        .getStringList(CONFIG_SECTION_PROJECT_MAPPINGS, subSection, CONFIG_PROJECT);

                        long matchingProjects = Arrays.asList(projectsList).stream().filter(str -> {
                                String[] parts = str.split("~");
                                String project = parts[0];
                                String branch;
                                if (parts.length > 1) {
                                        branch = parts[1];
                                } else {
                                        branch = "master";
                                }

                                return project.equalsIgnoreCase(projectName) && branch.equalsIgnoreCase(branchName);
                        }).count();
                        return matchingProjects > 0;
                }).findFirst().orElse(null);
        }
}