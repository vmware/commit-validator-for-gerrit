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
public class CommitTemplate {
    private List<TemplateEntry> mandatoryEntry;
    private List<TemplateEntry> optionalEntry;
}
