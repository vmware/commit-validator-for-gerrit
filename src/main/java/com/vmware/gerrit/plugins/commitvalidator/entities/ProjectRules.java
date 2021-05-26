package com.vmware.gerrit.plugins.commitvalidator.entities;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class ProjectRules {
    private boolean enabled;
    private String commitTemplate;
    private List<String> skipTemplateValidationFor;
    private List<String> additionalCodeReviewApprovalConditions;
    private List<String> additionalCodeReviewApprovers;
}
