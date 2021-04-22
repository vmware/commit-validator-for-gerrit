load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    _gerrit_plugin = "gerrit_plugin",
    _plugin_deps = "PLUGIN_DEPS",
)

gerrit_plugin = _gerrit_plugin
PLUGIN_DEPS = _plugin_deps
