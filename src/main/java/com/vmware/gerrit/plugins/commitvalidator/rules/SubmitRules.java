package com.vmware.gerrit.plugins.commitvalidator.rules;

import java.util.Optional;

import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitRecord.Status;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;

public class SubmitRules implements SubmitRule {
    public Optional<SubmitRecord> evaluate(ChangeData changeData) {
        // Implement your submitability logic here

        // Assuming we want to prevent this change from being submitted:
        SubmitRecord record = new SubmitRecord();
        record.status = Status.OK;
        return Optional.of(record);
    }
}