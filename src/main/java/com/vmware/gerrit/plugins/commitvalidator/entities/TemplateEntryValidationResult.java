package com.vmware.gerrit.plugins.commitvalidator.entities;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class TemplateEntryValidationResult {
    private TemplateEntryValidationStatus status;
    private String message;
}
