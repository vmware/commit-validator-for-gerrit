package com.vmware.gerrit.plugins.commitvalidator.config;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.vmware.gerrit.plugins.commitvalidator.entities.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.lib.Config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
public class CommitValidatorConfig {
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
        if (StringUtils.isEmpty(endpointName)) {
            return null;
        }

        // Check whether endpoint exists
        Config pluginConfig = cfg.getGlobalPluginConfig(Constants.CONFIG_FILENAME_WITHOUT_EXTN);
        Set<String> endpointKeys = pluginConfig
                .getNames(Constants.CONFIG_SECTION_JIRA_ENDPOINT, endpointName, true);

        if (endpointKeys.isEmpty()) {
            return null; // Endpoint doesn't exist
        }

        // Read Jira endpoint values
        String serverUrl = pluginConfig.getString(
                Constants.CONFIG_SECTION_JIRA_ENDPOINT, endpointName, Constants.CONFIG_ENDPOINT_URL);
        String username = pluginConfig.getString(
                Constants.CONFIG_SECTION_JIRA_ENDPOINT, endpointName,
                Constants.CONFIG_ENDPOINT_USERNAME);
        String password = pluginConfig.getString(
                Constants.CONFIG_SECTION_JIRA_ENDPOINT, endpointName,
                Constants.CONFIG_ENDPOINT_PASSWORD);

        return new JiraEndpoint(serverUrl, username, password);
    }

    /**
     * Fetches commit template details by name
     *
     * @param templateName
     * @return
     */
    public CommitTemplate getCommitTemplate(String templateName) {
        if (StringUtils.isEmpty(templateName)) {
            return null;
        }

        // Check whether template exists
        Config pluginConfig = cfg.getGlobalPluginConfig(Constants.CONFIG_FILENAME_WITHOUT_EXTN);
        Set<String> templateKeys = pluginConfig
                .getNames(Constants.CONFIG_SECTION_COMMIT_TEMPLATE, templateName, true);

        if (templateKeys.isEmpty()) {
            return null; // Template doesn't exist
        }

        // Read template values
        String[] mandatoryTemplateEntriesList = pluginConfig
                .getStringList(Constants.CONFIG_SECTION_COMMIT_TEMPLATE, templateName,
                        Constants.CONFIG_COMMIT_TEMPLATE_MANDATORY_ENTRY);
        String[] optionalTemplateEntriesList = pluginConfig
                .getStringList(Constants.CONFIG_SECTION_COMMIT_TEMPLATE, templateName,
                        Constants.CONFIG_COMMIT_TEMPLATE_OPTIONAL_ENTRY);

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
        if (StringUtils.isEmpty(entryName)) {
            return null;
        }

        // Check whether template entry exists
        Config pluginConfig = cfg.getGlobalPluginConfig(Constants.CONFIG_FILENAME_WITHOUT_EXTN);
        Set<String> templateEntryKeys = pluginConfig
                .getNames(Constants.CONFIG_SECTION_TEMPLATE_ENTRY, entryName, true);

        if (templateEntryKeys.isEmpty()) {
            return null; // Template entry doesn't exist
        }

        // Read template entry values
        String kindStr = pluginConfig.getString(
                Constants.CONFIG_SECTION_TEMPLATE_ENTRY, entryName,
                Constants.CONFIG_TEMPLATE_ENTRY_KIND);
        TemplateEntryKind kind = TemplateEntryKind.STR_SUB; // Default value;
        if (StringUtils.isNotEmpty(kindStr)) {
            kind = TemplateEntryKind.valueOf(kindStr.toUpperCase());
        }
        String key = pluginConfig.getString(
                Constants.CONFIG_SECTION_TEMPLATE_ENTRY, entryName,
                Constants.CONFIG_TEMPLATE_ENTRY_KEY);
        String value = pluginConfig.getString(
                Constants.CONFIG_SECTION_TEMPLATE_ENTRY, entryName,
                Constants.CONFIG_TEMPLATE_ENTRY_VALUE);

        String sampleValue = pluginConfig.getString(
                Constants.CONFIG_SECTION_TEMPLATE_ENTRY, entryName,
                Constants.CONFIG_TEMPLATE_ENTRY_SAMPLE_VALUE);
        String templateEntryTypeStr = pluginConfig
                .getString(Constants.CONFIG_SECTION_TEMPLATE_ENTRY, entryName,
                        Constants.CONFIG_TEMPLATE_ENTRY_TYPE);
        TemplateEntryType type = TemplateEntryType.STRING; // Default value
        if (StringUtils.isNotEmpty(templateEntryTypeStr)) {
            type = TemplateEntryType.valueOf(templateEntryTypeStr.toUpperCase());
        }
        boolean validateValAgainstEndpoint = pluginConfig
                .getBoolean(Constants.CONFIG_SECTION_TEMPLATE_ENTRY, entryName,
                        Constants.CONFIG_TEMPLATE_ENTRY_VALIDATE_VAL_AGAINST_ENDPOINT, false);
        String endpointTypeStr = pluginConfig.getString(
                Constants.CONFIG_SECTION_TEMPLATE_ENTRY, entryName,
                Constants.CONFIG_TEMPLATE_ENTRY_ENDPOINT_TYPE);
        EndpointType endpointType = null;
        if (StringUtils.isNotEmpty(endpointTypeStr)) {
            endpointType = EndpointType.valueOf(endpointTypeStr.toUpperCase());
        }

        String endpointName = pluginConfig.getString(
                Constants.CONFIG_SECTION_TEMPLATE_ENTRY, entryName,
                Constants.CONFIG_TEMPLATE_ENTRY_ENDPOINT_NAME);

        String[] allowedStatuses = ArrayUtils.nullToEmpty(pluginConfig.getStringList(
                Constants.CONFIG_SECTION_TEMPLATE_ENTRY, entryName,
                Constants.CONFIG_TEMPLATE_ENTRY_ALLOWED_STATUS));

        return new TemplateEntry(entryName, kind, type, key, value, sampleValue, validateValAgainstEndpoint,
                endpointType, endpointName, Arrays.asList(allowedStatuses));
    }

    /**
     * Fetches configured rules for given project and branch
     *
     * @param projectNameKey
     * @param branchName
     * @return
     * @throws NoSuchProjectException
     */
    public ProjectRules getProjectRules(Project.NameKey projectNameKey, String branchName) throws NoSuchProjectException {
        if (StringUtils.isEmpty(branchName)) {
            return null;
        }

        // Read project rules
        PluginConfig pluginConfig = cfg.getFromProjectConfig(projectNameKey, Constants.CONFIG_PROJECT_CONFIG_PLUGIN_SUB_SECTION);
        boolean enabled = pluginConfig.getBoolean(Constants.CONFIG_ENABLED, true);
        String[] branches = pluginConfig.getStringList(
                Constants.CONFIG_PROJECT_RULES_BRANCH);

        // If the branches are configured and given branch is not found, return nil.
        // If no branch is configured, consider it as for all branches of the repo.
        if (branches != null && !Arrays.asList(branches).contains(branchName)) {
            return null;
        }

        String commitTemplate = pluginConfig.getString(Constants.CONFIG_PROJECT_RULES_COMMIT_TEMPLATE);
        String[] skipTemplateValidationForAuthors = ArrayUtils.nullToEmpty(
                pluginConfig.getStringList(Constants.CONFIG_PROJECT_RULES_SKIP_TEMPLATE_VALIDATION_AUTHOR));
        String[] skipTemplateValidationForCommitters = ArrayUtils.nullToEmpty(
                pluginConfig.getStringList(Constants.CONFIG_PROJECT_RULES_SKIP_TEMPLATE_VALIDATION_COMMITTER));
        String[] additionalCRApprovalConditions = ArrayUtils.nullToEmpty(
                pluginConfig.getStringList(Constants.CONFIG_PROJECT_RULES_ADDITIONAL_CR_APPROVAL_IF));
        String[] additionalCodeReviewApprovers = ArrayUtils.nullToEmpty(
                pluginConfig.getStringList(Constants.CONFIG_PROJECT_RULES_ADDITIONAL_CR_APPROVERS));

        return new ProjectRules(enabled, commitTemplate, Arrays.asList(skipTemplateValidationForAuthors),
                Arrays.asList(skipTemplateValidationForCommitters),
                Arrays.asList(additionalCRApprovalConditions),
                Arrays.asList(additionalCodeReviewApprovers));
    }
}