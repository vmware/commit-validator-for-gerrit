package com.vmware.gerrit.plugins.commitvalidator;

import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.inject.AbstractModule;
import com.vmware.gerrit.plugins.commitvalidator.listeners.CommitValidator;
import com.vmware.gerrit.plugins.commitvalidator.rules.SubmitRules;

public class Module extends AbstractModule {
    @Override
    protected void configure() {
        DynamicSet.bind(binder(), CommitValidationListener.class).to(CommitValidator.class);
        bind(SubmitRule.class).annotatedWith(Exports.named("commit-validator")).to(SubmitRules.class);
    }
}
