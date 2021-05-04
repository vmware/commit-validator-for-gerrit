package com.vmware.gerrit.plugins.commitvalidator;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.inject.AbstractModule;
import com.vmware.gerrit.plugins.commitvalidator.listeners.CommitValidator;

public class Module extends AbstractModule {
  @Override
  protected void configure() {
    DynamicSet.bind(binder(), CommitValidationListener.class).to(CommitValidator.class);
  }
}
