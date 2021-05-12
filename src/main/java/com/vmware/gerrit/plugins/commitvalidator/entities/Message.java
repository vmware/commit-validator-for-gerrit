package com.vmware.gerrit.plugins.commitvalidator.entities;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Message {
    private List<MessageEntry> entries;

    @Override
    public String toString() {
        return this.entries.stream().map(entry -> {
            StringBuilder str = new StringBuilder();
            str.append(entry.getEntryName());
            str.append(":");

            if (!StringUtils.isEmpty(entry.getActualValue())) {
                str.append(entry.getActualValue());
            }
            str.append(" [Status:");
            str.append(entry.getEntryValidationStatus());
            str.append(" Type:");
            str.append(entry.getEntryType());

            if (!StringUtils.isEmpty(entry.getExample())) {
                str.append(" Example:");
                str.append(entry.getExample());
            }

            str.append("]\n");
            return str.toString();
        }).collect(Collectors.joining(""));
    }
}
