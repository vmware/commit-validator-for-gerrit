load("//tools/bzl:plugin.bzl", "gerrit_plugin")

gerrit_plugin(
    name = "commit-validator",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: commit-validator",
        "Gerrit-Module: com.vmware.gerrit.plugins.commitvalidator.Module",
        "Implementation-Title: Commit Validator",
    ],
    resources = glob(["src/main/resources/**/*"]),
)
