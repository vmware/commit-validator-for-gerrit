package com.vmware.gerrit.plugins.commitvalidator.entities;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class CommitTemplate {
    private List<TemplateEntry> mandatoryEntry;
    private List<TemplateEntry> optionalEntry;
}
