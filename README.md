# Ansible Tower Plugin

[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/ansible-tower.svg)](https://plugins.jenkins.io/ansible-tower)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/ansible-tower.svg?color=blue)](https://plugins.jenkins.io/ansible-tower)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/ansible-tower-plugin.svg?label=changelog)](https://github.com/jenkinsci/ansible-tower-plugin/releases/latest)

The Ansible Tower Plugin connects Jenkins to Ansible Tower, AWX, and Red Hat Ansible Automation Platform (AAP). It supports Freestyle projects and Pipeline jobs that need to:

- Launch job templates and workflow job templates.
- Wait for jobs and import controller output.
- Run project syncs.
- Change a project's SCM revision.
- Pass Jenkins environment variables to launch parameters.
- Return job metadata and exported Ansible values to Jenkins.

## Requirements

- Jenkins 2.479.1 or newer.
- Java 21 or newer.
- A reachable Tower, AWX, or AAP controller API.
- A Jenkins credential with sufficient controller permissions.

For local development, use JDK 21 and Maven 3.9.6 or newer.

## Installation

Install **Ansible Tower** from **Manage Jenkins → Plugins**, or upload a locally built `ansible-tower.hpi` from the plugin manager's advanced settings.

After installation, configure at least one controller under **Manage Jenkins → System → Ansible Tower**.

## Global configuration

Each configured installation has the following fields:

| Field | Description |
| --- | --- |
| Name | Name used by Freestyle builds and the Pipeline `towerServer` parameter. |
| URL | Controller or AAP gateway base URL, without an API path; for example `https://aap.example.com`. |
| API Base Path | Use `/api/v2` for Tower/AWX or `/api/controller/v2` for AAP 2.5+ controller APIs. |
| Credentials | Default Jenkins credential for this installation. Pipeline and Freestyle steps can override it. |
| Force Trust Cert | Disables normal TLS certificate validation. Use only for controlled test environments. |
| Enable Debugging | Allows detailed `FINE` diagnostics. A Jenkins Log Recorder must also enable `FINE` or `ALL`. |

Use **Test Connection** to verify the URL, API path, TLS setting, and credential.

### AAP 2.5+

Configure an AAP gateway/controller installation as follows:

```text
URL: https://aap-gateway.example.com
API Base Path: /api/controller/v2
```

Controller calls then use `/api/controller/v2/...`. OAuth tokens created from username/password credentials use the AAP gateway endpoint `/api/gateway/v1/tokens/`.

The configured API path also controls generated UI links:

- Tower/AWX workflow: `/#/jobs/workflow/<id>`
- AAP workflow: `/execution/jobs/workflow/<id>/output`
- AAP job: `/execution/jobs/playbook/<id>/output`

## Authentication

The plugin accepts these Jenkins credential types:

### Username with password

When a username/password credential is selected, the plugin attempts authentication in this order:

1. Create an OAuth token.
2. Try the legacy authtoken endpoint when supported.
3. Fall back to HTTP Basic authentication.

The account should be a dedicated service account with only the permissions required to read and launch the configured resources.

### Secret text bearer token

Store the actual OAuth access token value in a Jenkins **Secret text** credential. The plugin sends it as a bearer token and does not create or delete that externally managed token.

Secret text credentials require the [Plain Credentials Plugin](https://plugins.jenkins.io/plain-credentials/).

Do not enter a token database ID or token record ID; Jenkins needs the access token value.

## Run a job or workflow template

Freestyle projects provide an **Ansible Tower** build step. Pipeline jobs use `ansibleTower`.

The minimum Pipeline configuration is `towerServer`, `towerCredentialsId`, `jobTemplate`, and `jobType`. `towerCredentialsId` may be an empty string to use the globally configured credential.

```groovy
def result = ansibleTower(
    towerServer: 'AAP UAT',
    towerCredentialsId: '',
    jobTemplate: 'Deploy application',
    jobType: 'run',
    templateType: 'job',
    towerLogLevel: 'full',
    extraVars: '''---
environment: uat
release: "${BUILD_TAG}"
''',
    inventory: 'UAT Inventory',
    removeColor: true,
    throwExceptionWhenFail: true,
    async: false
)

echo "AAP job ${result.JOB_ID}: ${result.JOB_URL}"
echo "Result: ${result.JOB_RESULT}"
```

### Parameters

| Parameter | Values/default | Description |
| --- | --- | --- |
| `towerServer` | Required | Name of a globally configured installation. |
| `towerCredentialsId` | Required; `''` uses global credential | Optional per-build credential override. |
| `jobTemplate` | Required | Template name or numeric ID. |
| `jobType` | `run` or `check`; default `run` | Job launch type. |
| `templateType` | `job` or `workflow`; default `job` | Selects job template or workflow job template APIs. |
| `extraVars` | Empty | YAML or JSON launch variables. |
| `inventory` | Empty | Inventory name or numeric ID. |
| `credential` | Empty | Controller credential name or numeric ID. Multiple credentials may be comma-separated where supported. |
| `limit` | Empty | Host limit passed at launch. |
| `jobTags` | Empty | Job tags passed at launch. |
| `skipJobTags` | Empty | Tags to skip. |
| `scmBranch` | Empty | SCM branch override. The template must allow prompting for SCM branch. |
| `towerLogLevel` | `false` | Output import mode described below. |
| `importWorkflowChildLogs` | `false` | Imports child job output for workflows. |
| `removeColor` | `false` | Removes ANSI color sequences from imported output. |
| `verbose` | `false` | Adds progress messages, including some expanded launch values, to the Jenkins build console. Avoid placing secrets in launch parameters when this is enabled. |
| `throwExceptionWhenFail` | `true` | Fails the Pipeline step when the controller operation fails. |
| `async` | `false` | Returns immediately with a job handle. |

The controller template must permit prompting for any launch-time overrides you provide, such as inventory, credential, variables, tags, limit, job type, or SCM branch.

### Output import modes

| `towerLogLevel` | Behavior |
| --- | --- |
| `false` | Do not import controller output. |
| `true` | Import the output exposed by the controller UI. Long lines may be truncated. |
| `full` | Import full event output where available. |
| `vars` | Process exported variables without printing controller output. |

The legacy Boolean `importTowerLogs` option remains supported for compatibility, but new Pipeline code should use `towerLogLevel`.

## Project operations

### Project sync

Freestyle projects provide **Ansible Tower Project Sync**. Pipeline jobs use `ansibleTowerProjectSync`:

```groovy
def result = ansibleTowerProjectSync(
    towerServer: 'AAP UAT',
    towerCredentialsId: '',
    project: 'Application Project',
    importTowerLogs: true,
    removeColor: true,
    throwExceptionWhenFail: true,
    async: false,
    verbose: false
)

echo "Sync ${result.SYNC_ID}: ${result.SYNC_URL}"
echo "Result: ${result.SYNC_RESULT}"
```

### Project revision

Freestyle projects provide **Ansible Tower Project Revision**. Pipeline jobs use `ansibleTowerProjectRevision`:

```groovy
ansibleTowerProjectRevision(
    towerServer: 'AAP UAT',
    towerCredentialsId: '',
    project: 'Application Project',
    revision: 'release/1.4',
    throwExceptionWhenFail: true,
    verbose: false
)
```

Changing the revision may cause the controller to synchronize an SCM-backed project automatically. A manual project may accept the revision request without performing an SCM operation.

## Asynchronous execution

With `async: true`, a launch returns before the controller operation completes.

An asynchronous `ansibleTower` result contains:

- `JOB_ID`
- `JOB_URL`
- `job`, a `TowerJob` handle

```groovy
def submitted = ansibleTower(
    towerServer: 'AAP UAT',
    towerCredentialsId: '',
    jobTemplate: 'Long deployment',
    jobType: 'run',
    templateType: 'workflow',
    async: true
)

def job = submitted.get('job')
try {
    timeout(time: 30, unit: 'MINUTES') {
        waitUntil {
            job.isComplete()
        }
    }

    job.getLogs().each { line -> echo line }
    if (!job.wasSuccessful()) {
        error 'AAP workflow failed'
    }
} finally {
    job.releaseToken()
}
```

`TowerJob` exposes `isComplete()`, `wasSuccessful()`, `getLogs()`, `getExports()`, `cancelJob()`, and `releaseToken()`.

An asynchronous project sync result contains `SYNC_ID`, `SYNC_URL`, and a `sync` handle. `TowerProjectSync` exposes `isComplete()`, `wasSuccessful()`, `getLogs()`, `cancelSync()`, `getURL()`, `getID()`, and `releaseToken()`.

Jenkins may require Script Security approvals before untrusted Pipeline code can invoke methods on these returned Java objects.

When username/password authentication creates a temporary token, async mode releases the launch token before returning. Later handle calls may acquire another token; call `releaseToken()` when the handle is no longer needed.

## Returned values

Synchronous job results are returned as a `Properties`-like object containing:

| Key | Availability | Description |
| --- | --- | --- |
| `JOB_ID` | Always after launch | Controller job ID. |
| `JOB_URL` | Always after launch | Link to controller job output. |
| `JOB_RESULT` | Synchronous execution | `SUCCESS` or `FAILED`. |
| Exported keys | After export processing | Values produced through `JENKINS_EXPORT`. |

Project sync results similarly contain `SYNC_ID`, `SYNC_URL`, and, for synchronous execution, `SYNC_RESULT`.

### Export data from Ansible

The plugin recognizes a purpose-driven output line:

```yaml
- name: Export a value to Jenkins
  ansible.builtin.debug:
    msg: "JENKINS_EXPORT IMAGE_TAG=1.4.2"
```

It also recognizes controller artifacts created with `set_stats`:

```yaml
- name: Export structured values to Jenkins
  ansible.builtin.set_stats:
    data:
      JENKINS_EXPORT:
        - image_tag: "1.4.2"
        - deployment_id: "deploy-42"
    aggregate: true
    per_host: false
```

Pipeline jobs receive exported values in the returned result. Freestyle jobs can inject them through the optional [EnvInject Plugin](https://plugins.jenkins.io/envinject/). EnvInject has limited Pipeline compatibility, so Pipeline code should use the returned result instead of relying on `env`.

## Jenkins environment variable expansion

The plugin expands Jenkins environment variables in these inputs before calling the controller:

- Job template
- Extra variables
- Limit
- Job and skip tags
- Inventory
- Controller credential
- SCM branch
- Project and project revision

Example:

```yaml
release: "$BUILD_TAG"
environment: "$DEPLOY_ENV"
```

## Console color

Set `removeColor: true` to strip ANSI sequences. To preserve and render colors, install the [AnsiColor Plugin](https://plugins.jenkins.io/ansicolor/) and configure the Pipeline accordingly.

## Logging and troubleshooting

Plugin diagnostics use `java.util.logging` under this namespace:

```text
org.jenkinsci.plugins.ansible_tower
```

To collect detailed diagnostics:

1. Select **Enable Debugging** on the global Tower installation.
2. Open **Manage Jenkins → System Log**.
3. Add a Log Recorder for `org.jenkinsci.plugins.ansible_tower` at `FINE` or `ALL`.

Operational `WARNING` and `SEVERE` records are emitted even when debugging is disabled. Messages retain the `[Ansible-Tower]` prefix. System diagnostics do not log request/response payloads, credentials, authorization headers, or tokens. The separate `verbose` build-console option may print expanded launch values.

For Jenkins installed as a Linux systemd service:

```shell
sudo journalctl -u jenkins --since "24 hours ago" --no-pager \
  | grep -F '[Ansible-Tower]'
```

To find transient gateway failures and retries:

```shell
sudo journalctl -u jenkins --no-pager \
  | grep -E '\[Ansible-Tower\].*(502|503|504|attempt=|exhausted retries)'
```

Job status and workflow event polling retry HTTP 502, 503, and 504 responses up to five times with a ten-second delay. Exhausted retries are logged at `SEVERE`.

### Common checks

- A `401` normally indicates an invalid or expired credential.
- A `403` normally indicates insufficient controller permissions.
- A `404` often indicates the wrong API Base Path or a resource that does not exist.
- Repeated `502`, `503`, or `504` responses usually point to the AAP gateway, route, load balancer, or controller availability rather than a Jenkins credential issue.
- If launch overrides are ignored, enable the corresponding **Prompt on launch** option on the controller template.

## Development

Run the test suite:

```shell
mvn test
```

Build and verify the plugin:

```shell
mvn verify
```

The HPI is generated at `target/ansible-tower.hpi`.

## Support and contributions

- Report reproducible defects through [GitHub Issues](https://github.com/jenkinsci/ansible-tower-plugin/issues).
- Review published changes in [GitHub Releases](https://github.com/jenkinsci/ansible-tower-plugin/releases).
- Contributions should include tests for behavior changes and pass `mvn verify`.

This project is licensed under the MIT License.
