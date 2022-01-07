package com.vmware.gerrit.plugins.commitvalidator.entities;

public class InvalidEntryException extends Exception {
    public InvalidEntryException(String errorMessage) {
        super(errorMessage);
    }
}
