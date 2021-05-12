package com.vmware.gerrit.plugins.commitvalidator.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class MessageEntry {
    private String entryName;
    private String actualValue;
    private TemplateEntryType entryType;
    private TemplateEntryValidationStatus entryValidationStatus;
    private String example;
}
