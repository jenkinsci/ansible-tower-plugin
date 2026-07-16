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

- Jenkins 2.504.3 or newer. Compatibility is also verified against the Jenkins 2.554 weekly release.
- Java 17 or newer at runtime, as derived from the Jenkins 2.504.3 baseline.
- JDK 21 and Maven 3.9.6 or newer to build the plugin locally. Jenkins CI and the official Maven CD workflow also use JDK 21.
- A reachable Tower, AWX, or AAP controller API.
- A Jenkins credential with sufficient controller permissions.

For local development, use JDK 21 and Maven 3.9.6 or newer.

## Installation

1. Open **Manage Jenkins → Plugins**.
2. Search the **Available plugins** tab for **Ansible Tower**.
3. Install the plugin and restart Jenkins if requested.
4. Alternatively, upload a locally built `ansible-tower.hpi` from the plugin manager's advanced settings.

After installation, configure at least one controller under **Manage Jenkins → System → Ansible Tower**.

## Global configuration

1. Open **Manage Jenkins → System**.
2. Find the **Ansible Tower** section.
3. Select **Add Tower Installation**.
4. Enter the installation name, URL, API Base Path, credential, and TLS/debug options.
5. Select **Test Connection**.
6. Save the Jenkins system configuration after the test succeeds.

![Ansible Tower global configuration](./docs/images/configuration-0.7.0.png)

> The screenshot is from an earlier plugin release. Current releases also display the **API Base Path** selector used for Tower/AWX and AAP 2.5+ compatibility.

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

To configure username/password authentication:

1. Open **Manage Jenkins → Credentials**.
2. Add a **Username with password** credential.
3. Use a dedicated controller service account with only the required permissions.
4. Select the new credential in the global Ansible Tower installation.
5. Use **Test Connection** to validate it.

When a username/password credential is selected, the plugin attempts authentication in this order:

1. Create an OAuth token.
2. Try the legacy authtoken endpoint when supported.
3. Fall back to HTTP Basic authentication.

The account should be a dedicated service account with only the permissions required to read and launch the configured resources.

### Secret text bearer token

To configure a pre-created bearer token:

1. Create an OAuth access token for the Jenkins service account in Tower, AWX, or AAP.
2. Copy the access token value when the controller displays it.
3. Open **Manage Jenkins → Credentials**.
4. Add a **Secret text** credential and paste the access token into **Secret**.
5. Select the new credential in the global Ansible Tower installation.
6. Use **Test Connection** to validate it.

Store the actual OAuth access token value in a Jenkins **Secret text** credential. The plugin sends it as a bearer token and does not create or delete that externally managed token.

Secret text credentials require the [Plain Credentials Plugin](https://plugins.jenkins.io/plain-credentials/).

Do not enter a token database ID or token record ID; Jenkins needs the access token value.

## Run a job or workflow template

Freestyle projects provide an **Ansible Tower** build step. Pipeline jobs use `ansibleTower`.

### Freestyle configuration

1. Open or create a Freestyle project.
2. Select **Configure**.
3. Under **Build Steps**, select **Add build step → Ansible Tower**.
4. Choose the configured Tower/AWX/AAP server and optional credential override.
5. Select `job` or `workflow`, then enter the template name or numeric ID.
6. Configure launch overrides and output import behavior as required.
7. Save and run the project.

![Run an Ansible Tower job from a Freestyle project](./docs/images/run_job_freestyle.png)

### Pipeline configuration

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
deployment_environment: uat
release: "${BUILD_TAG}"
''',
    inventory: 'UAT Inventory',
    removeColor: true,
    throwExceptionWhenFail: true,
    async: false
)

// The step currently returns java.util.Properties. Printing the whole result is
// Sandbox-safe and includes JOB_ID, JOB_URL, JOB_RESULT, and LOG_IMPORT_RESULT.
echo "AAP result: ${result}"
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

For a Freestyle project:

1. Open the project configuration.
2. Select **Add build step → Ansible Tower Project Sync**.
3. Choose the Tower server and optional credential override.
4. Enter the controller project name.
5. Select output, color, failure, and async behavior.
6. Save and run the project.

![Synchronize an Ansible Tower project from Freestyle](./docs/images/project_sync_freestyle.png)

Pipeline example:

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

echo "Project sync result: ${result}"
```

### Project revision

Freestyle projects provide **Ansible Tower Project Revision**. Pipeline jobs use `ansibleTowerProjectRevision`:

For a Freestyle project:

1. Open the project configuration.
2. Select **Add build step → Ansible Tower Project Revision**.
3. Choose the Tower server and optional credential override.
4. Enter the project name and desired SCM revision.
5. Select failure and verbose behavior.
6. Save and run the project.

![Change an Ansible Tower project revision from Freestyle](./docs/images/project_revision_freestyle.png)

Pipeline example:

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
- `LOG_IMPORT_RESULT` with value `DEFERRED`
- `job`, a `TowerJob` handle

The advanced async example below accesses a Java object returned by the plugin.
In a sandboxed Pipeline, an administrator must first approve
`method java.util.Dictionary get java.lang.Object` and may also need to approve
the `TowerJob` methods used by the Pipeline. Synchronous Pipelines do not need
these approvals when they only print the complete result as shown above.

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

Jenkins may require additional Script Security approvals before untrusted Pipeline code can invoke methods on these returned Java objects.

When username/password authentication creates a temporary token, async mode releases the launch token before returning. Later handle calls may acquire another token; call `releaseToken()` when the handle is no longer needed.

## Returned values

Synchronous job results are returned as a `java.util.Properties` object containing:

| Key | Availability | Description |
| --- | --- | --- |
| `JOB_ID` | Always after launch | Controller job ID. |
| `JOB_URL` | Always after launch | Link to controller job output. |
| `JOB_RESULT` | Synchronous execution | `SUCCESS` or `FAILED`. |
| `LOG_IMPORT_RESULT` | Always after launch | `SKIPPED`, `DEFERRED`, `SUCCESS`, or `PARTIAL`. |
| Exported keys | After export processing | Values produced through `JENKINS_EXPORT`. |

`JOB_RESULT` reflects only the final AAP job result. Event import is best-effort: if AAP
finishes successfully while its event or workflow-node endpoints remain unavailable,
the step returns `JOB_RESULT=SUCCESS` and `LOG_IMPORT_RESULT=PARTIAL`.

Project sync results similarly contain `SYNC_ID`, `SYNC_URL`, and, for synchronous execution, `SYNC_RESULT`.

Groovy property access such as `result.JOB_ID`, `result['JOB_ID']`, or
`result.get('JOB_ID')` can resolve to `java.util.Dictionary.get(Object)` and be
rejected by Jenkins Pipeline Sandbox. Print the complete result, or have an
administrator review and approve that signature under **Manage Jenkins →
In-process Script Approval**.

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

Logging is split between two audiences. The build console always shows user-facing
milestones, retry notices, and failures needed to diagnose the current run. Jenkins
System Log keeps lower-level diagnostics; successful HTTP request metadata is
available at `FINE`, while operational `WARNING` and `SEVERE` records are emitted
even when debugging is disabled. Messages retain the `[Ansible-Tower]` prefix.

Neither destination logs request or response payloads, extra variables, credentials,
authorization headers, passwords, or tokens. Endpoint query values are redacted.
The `verbose` build-console option adds safe expansion and launch progress details,
but does not expose expanded extra variables or credential values.

Typical build-console diagnostics look like:

```text
[Ansible-Tower] INFO: Resolving job template: template=deploy
[Ansible-Tower] ERROR: HTTP request completed: method=GET, url=https://aap.example.com/api/controller/v2/job_templates/?name=<redacted>, httpStatus=502, durationMs=103
[Ansible-Tower] ERROR: Unable to lookup job template; the job was not launched: GET https://aap.example.com/api/controller/v2/job_templates/?name=<redacted> returned HTTP 502
```

If a launch request receives a transient gateway response or loses its connection,
the console explicitly reports that the launch outcome is unknown. The plugin does
not automatically retry that POST because the controller may already have created
the job:

```text
[Ansible-Tower] WARNING: Tower launch outcome is unknown; the controller may have created the job, but Jenkins received HTTP 502. Automatic retry was not performed.
```

Polling recovery continues to report both the retry and recovery in the build
console, including the job ID, endpoint, status, attempt, and retry delay.

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

Synchronous jobs poll status every five seconds and events every ten seconds. Status
HTTP 502, 503, and 504 responses back off for 5, 10, 20, 30, and 30 seconds before
the status path fails. Event failures use an independent 10, 20, then 30-second
backoff and never delay status polling. After completion, event import is attempted
immediately and, for transient failures, again after two and five seconds.

All controller HTTP clients use a 10-second connect timeout, a 10-second connection
pool timeout, and a 30-second read timeout. Transport timeouts, refused/reset
connections, and empty HTTP responses are treated as transient; TLS, URL, and
configuration errors are not.

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
