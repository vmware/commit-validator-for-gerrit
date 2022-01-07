package com.vmware.gerrit.plugins.commitvalidator.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@ToString
public class TemplateEntry {
    private String name;
    private TemplateEntryKind kind;
    private TemplateEntryType type;
    private String key;
    private String value;
    private String exampleValue;
    private boolean validateValueAgainstEndpoint;
    private EndpointType endpointType;
    private String endpointName;
    private List<String> allowedStatuses;
}
