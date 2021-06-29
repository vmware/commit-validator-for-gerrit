package com.vmware.gerrit.plugins.commitvalidator.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class Message {
    private List<MessageEntry> entries;

    @Override
    public String toString() {

        StringBuilder subEntries = new StringBuilder();
        StringBuilder bodyEntries = new StringBuilder();
        StringBuilder keyValEntries = new StringBuilder();
        for (MessageEntry entry : this.entries) {
            switch (entry.getKind()) {
                case STR_SUB:
                    subEntries.append(String.format("- %s for ", getValidationStatusStr(entry)));
                    subEntries.append(String.format("'%s'", entry.getEntryName()));
                    if (!StringUtils.isEmpty(entry.getExample())) {
                        subEntries.append(" {Example: ");
                        subEntries.append(String.format("'%s'", entry.getExample()));
                        subEntries.append("}");
                    }
                    subEntries.append(System.lineSeparator());
                    break;
                case STR_BODY:
                    bodyEntries.append(String.format("- %s for ", getValidationStatusStr(entry)));
                    bodyEntries.append(String.format("'%s'", entry.getEntryName()));
                    if (!StringUtils.isEmpty(entry.getExample())) {
                        bodyEntries.append(" {Example: ");
                        bodyEntries.append(String.format("'%s'", entry.getExample()));
                        bodyEntries.append("}");
                    }
                    bodyEntries.append(System.lineSeparator());
                    break;
                case KEY_VAL:
                    keyValEntries.append(String.format("- %s for ", getValidationStatusStr(entry)));
                    keyValEntries.append(String.format("'%s'", entry.getEntryName()));
                    if (!StringUtils.isEmpty(entry.getExample())) {
                        keyValEntries.append(" {Example: ");
                        keyValEntries.append(String.format("'%s'", entry.getExample()));
                        keyValEntries.append("}");
                    }
                    keyValEntries.append(System.lineSeparator());
                    break;
            }
        }

        StringBuilder message = new StringBuilder();
        if (subEntries.length() > 0) {
            message.append(System.lineSeparator());
            message.append("Issues in commit SUBJECT:");
            message.append(System.lineSeparator());
            message.append("-------------------------");
            message.append(System.lineSeparator());
            message.append(subEntries.toString());
        }
        if (bodyEntries.length() > 0 || keyValEntries.length() > 0) {
            message.append(System.lineSeparator());
            message.append("Issues in commit MESSAGE BODY:");
            message.append(System.lineSeparator());
            message.append("------------------------------");
            if (bodyEntries.length() > 0) {
                message.append(System.lineSeparator());
                message.append(bodyEntries.toString());
            }

            if (keyValEntries.length() > 0) {
                message.append(System.lineSeparator());
                message.append(keyValEntries.toString());
                message.append(System.lineSeparator());
            }
        }
        return message.toString();
    }


    private String getValidationStatusStr(MessageEntry entry) {
        switch (entry.getEntryValidationStatus()) {
            case MISSING_KEY:
                return "MISSING key and value";
            case MISSING_VALUE:
                if (entry.getKind() == TemplateEntryKind.KEY_VAL) {
                    return "Key is present but value is MISSING";
                }
                return "MISSING Value";
            case INVALID_VALUE:
                if (entry.getKind() == TemplateEntryKind.KEY_VAL) {
                    return "Key is present but value is INVALID";
                }
                return "INVALID Value";
        }
        return "";
    }
}