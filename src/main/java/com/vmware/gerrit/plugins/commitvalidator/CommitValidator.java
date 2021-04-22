package com.vmware.gerrit.plugins.commitvalidator;

import java.util.List;
 
import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.inject.Inject; 
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommitValidator implements CommitValidationListener {
  private static final Logger log = LoggerFactory.getLogger(CommitValidator.class);

  @Inject
  private PluginConfigFactory pluginConfigFactory;

  @Inject
  protected GerritApi gerritApi; 

  @Override
  public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
      throws CommitValidationException {

    String projectName = receiveEvent.project.getName();
    String branchName = receiveEvent.getBranchNameKey().branch().replaceFirst("refs/heads/", "");
    String commitSubject = receiveEvent.commit.getShortMessage();
    String commitMessageBody = receiveEvent.commit.getFullMessage();

    log.info(String.format("Change Details: project - %s, branch - %s, subject - %s, message - %s", projectName, branchName, commitSubject,commitMessageBody));

    return ImmutableList.of();
  }
}
