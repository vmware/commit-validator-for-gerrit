# Commit Validator for Gerrit
A gerrit plugin to validate commits aginst predefined template rules.

## Installation

* place `commit-validator-x.x.x.jar` under `plugins` folder
* place `commit-validator.secure.config` and `commit-validator.config` files under `etc`folder

## Configuration
Below are the sample configuration files:

commit-validator.secure.config
```
[endpoint-jira "default"]
    url = https://jira.<company>.com
    username = <jiraUser>
    password = <jiraPassword>
```
commit-validator.config
```
# Template entry definitions
[template-entry "jira-issue"]
    value = [[A-Z]+[-][0-9]+]
    validateAgainstEndpoint = true
    endpointType = JIRA
    endpointName = default

[template-entry "modifies-api-request"]
    key = Modifies API Request
    value = boolean

[template-entry "modifies-api-response"]
    key = Modifies API Response
    value = boolean

[template-entry "modifies-data-at-rest"]
    key = Modifies data at rest
    value = boolean

[template-entry "modifies-existing-test-case"]
    key = Modifies existing test case
    value = boolean

# Commit Template definitions
[commit-template "default"]
    mandatoryEntry = jira-issue
    mandatoryEntry = modifies-api-request
    mandatoryEntry = modifies-api-response
    mandatoryEntry = modifies-data-at-rest
    optionalEntry = modifies-existing-test-case

[commit-template "template1"]
    mandatoryEntry = modifies-api-request
    mandatoryEntry = modifies-api-response
    optionalEntry = modifies-existing-test-case


# Projects to Template mappings
[project-mappings "default"]
    enabled = true
    project = test-plugin
    project = project2~master
    project = project3~otherbranch

[project-mappings "template1"]
    enabled = false
    project = project4
    project = project5~master
    project = project6~otherbranch1
```

## Contributing

The Commit Validator for Gerrit project team welcomes contributions from the community. If you wish to contribute code and you have not signed our contributor license agreement (CLA), our bot will update the issue when you open a Pull Request. For any questions about the CLA process, please refer to our [FAQ](https://cla.vmware.com/faq).

For more details about contributing, refer to the [contributing guidelines](https://github.com/vmware/commit-validator-for-gerrit/blob/master/LICENSE.txt)

## License

Apache License 2.0, see [LICENSE](https://github.com/vmware/commit-validator-for-gerrit/blob/master/LICENSE.txt).
