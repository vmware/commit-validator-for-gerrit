package com.vmware.gerrit.plugins.commitvalidator.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TemplateEntry {
    private String key;
    private String value;
    private TemplateEntryType type;
    private boolean validateAgainstEndpoint;
    private EndpointType endpointType;
    private String endpointName;
}
