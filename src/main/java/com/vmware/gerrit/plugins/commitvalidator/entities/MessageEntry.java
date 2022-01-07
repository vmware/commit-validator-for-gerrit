package com.vmware.gerrit.plugins.commitvalidator.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class MessageEntry {
    private String entryName;
    private List<String> actualValues;
    private TemplateEntryKind kind;
    private TemplateEntryType entryType;
    private TemplateEntryValidationStatus entryValidationStatus;
    private String validationMessage;
    private String example;
}
