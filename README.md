Ansible Tower Plugin
====================

[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/ansible-tower.svg)](https://plugins.jenkins.io/ansible-tower)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/ansible-tower-plugin.svg?label=changelog)](https://github.com/jenkinsci/ansible-tower-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/ansible-tower.svg?color=blue)](https://plugins.jenkins.io/ansible-tower)

## About this plugin

This plugin connects Jenkins to [Ansible Tower](http://www.ansible.com/) to do the following things:
* Run jobs as a build step
* Update projects

## Configuration

After installing the plugin you can configure Ansible Tower servers in the Jenkins Global Configuration under the section *Ansible Tower* by clicking the add button.

![Configure Plugin](/docs/images/configuration-0.7.0.png)

The fields are as follows:

| Field Name  | Description |
|-------------|----------------|
| Credentials | The credentials within Jenkins to be used to connect to the Ansible Tower server.<br/>* For basic auth use a "Username with password" type credential<br/>* For oAuth (starting in Tower 3.3.0) use a "Secret Text" type credential from the [Plain Credentials Plugin](https://plugins.jenkins.io/plain-credentials)<br/>See the OAuth Authentication section of this document for more details on setting up oAuth.|
| Enable Debugging | This will allow the plugin to write detailed messages into the jenkins.log for debugging purposes. It will show show requests to the server and payloads. This can contain a lot of data. All messages are prefixed with \[Ansible-Tower].<br/><br/>For Example:<br/><br/>[Ansible-Tower] building request to https://192.168.56.101/api/v1/workflow_jobs/200/workflow_nodes/<br/>[Ansible-Tower] Adding auth for admin<br/>[Ansible-Tower] Forcing cert trust</br> [Ansible-Tower] {"count":4,"next":null ...</br>|
| Force Trust Cert | If your Ansible Tower instance is using an https cert that Jenkins does not trust, and you want the plugin to trust the cert anyway, you can click this box.<br/><br/>You should really understand the implications if you are going to use this option. Its meant for testing purposes only. |
| Name | The name that this Ansible Tower installation will be referenced as. |
| URL | The base URL of the Ansible Tower server.|

Once the settings are completed, you can test the connection between Jenkins and Ansible Tower by clicking on the Test Connection button.

## Basic Authentication

This plugin can use a username password to attempt to authenticate with Tower. As of plugin version 0.13.0 when using username/password the plugin will attempt to:
  * Pull an OAuth2 token
  * Attempt to use a pre-oauth authtoken
  * Fall back to basic auth

If you are using plugin version >= 0.13.0 with Basic Auth and pipelines leveraging the async method please see the note at the bottom of section `Async Execution` for details about freeing Tower tokens.

## OAuth Authentication

Starting in Tower version 3.3.0, Oauth Authentication can be used alongside basic auth. Beginning in Tower version 3.4.0, **basic authentication will be disabled**.

The [Ansible Tower Documentation](https://docs.ansible.com/ansible-tower/latest/html/userguide/applications_auth.html)
 covers this in detail, but here is a rough outline of what needs to be performed.

Tower Configuration

1. Add an Application for the Jenkins Oauth tokens
   * Name (i.e. "Jenkins")
   * Description (optional)
   * Organization (The organization which covers the JTs and WFJTs you will be using)
   * Authorization Grant Type = Resource owner password-based
   * Client Type = Confidential
1. Add or create a user to bind to an Oauth token
   * This should be a service account with limited permissions.
   * Add a Token for this user with the Users / <Username> / Tokens / Create Token dialogue (with the appropriate scope)
   * Copy the Token ID which is generated on the prompt.

Jenkins Configuration

1. Install this plugin (it must be a version >= 0.90)
1. Add a Credential
   * Choose the appropriate scope
   * Secret = Token ID from step 2.c above
   * ID = Something to denote it's purpose (i.e. "jenkins-tower-token")
   * Description (optional)
1. Complete the configuration steps as defined in this guide's 'Configuration'

## Job Execution

### Adding a Build Step

In a freestyle project a new build step called Ansible Tower is now available:

![Run Job Build Step](/docs/images/run_job_freestyle.png)

| Field | Description |
|-------|-------------|
| Tower Server | The predefined Ansible Tower server to run the template on.|
| Tower Credentials Override | Overrides the credentials from global Ansible Tower configuration.|
| Template Type | Whether you are running a job or workflow template.|
| Template ID | The name or numerical ID of the template to be run on the Ansible Tower server.|
| Extra Vars | Additional variables to be passed to the job. I.e.:<br/>---<br/>my_var: This is a variable called my_var|
| Job Tags | Any job tags to be passed to Ansible Tower.|
|Skip Job Tags | Any skip tags to be passed to Ansible Tower.|
|Job Type | Is this a template run or a check.|
|Limit | The servers to limit the invocation to.|
|Inventory | The name or numeric ID of the inventory to run the job on.|
| Credential | The name or numeric ID of the credentials to run the job with.|
| Verbose | Add additional messages to the Jenkins console about the job run.|
| Import Tower Output | Pull all of the logs from Ansible Tower into the Jenkins console.|
| Import Workflow Child Output | Pull in the output from all of the jobs that the template runs.|
| Remove Color | When importing the Ansible Tower output, strip off the ansi color encodings.|

### Pipeline support
Tower jobs can be executed from workflow scripts.
The towerServer and jobTemplate are the only required parameters.

```groovy
node {
    stage('MyStage') {
        ansibleTower(
            towerServer: 'Prod Tower',
            towerCredentialsId: '',
            templateType: 'job',
            jobTemplate: 'Simple Test',
            importTowerLogs: true,
            inventory: 'Demo Inventory',
            jobTags: '',
            skipJobTags: '',
            limit: '',
            removeColor: false,
            verbose: true,
            credential: '',
            extraVars: '''---
my_var:  "Jenkins Test"''',
            async: false
        )
    }
}
```

## Project Update

### Adding a Build Step
In a freestyle project a new build called Ansible Tower Project Sync is now available:

![Project Sync Build Step](/docs/images/project_sync_freestyle.png)

| Field | Description |
|-------|-------------|
| Tower Server | The predefined Ansible Tower server to run the sync on. |
| Tower Credentials Override | Overrides the credentials from global Ansible Tower configuration. |
| Project Name | The name of the project to perform the SCM sync. |
| Verbose | Add additional messages to the Jenkins console about the job run.|
| Import Tower Output | Pull all of the logs from Ansible Tower into the Jenkins console.|
| Remove Color | When importing the Ansible Tower output, strip off the ansi color encodings.|

### Pipeline Support
Project syncs can be executed from workflow scripts. The ansibleTowerProjectSync function is made available through this plugin. The towerServer and project parameters are the only ones required.
```groovy
        projectSyncResults = ansibleTowerProjectSync(
            async: true,
            importTowerLogs: true,
            project: 'Demo Project',
            removeColor: false,
            throwExceptionWhenFail: false,
            towerServer: 'EC2 AWX 8',
            verbose: false
        )
```

## Project Revision

**Note:** You can run a project revision on a non-scm project. This will not raise an error to Jenkins as the Tower API will allow this to happen. 

**Note:** Tower will auto sync the project on the change of the revision. Therefore calling Ansible Tower Project Sync in a job after the update is redundant.

### Adding a Build Step
In a freestyle project a new build called Ansible Tower Project Revision is now available:

![Project Revision Build Step](/docs/images/project_revision_freestyle.png)

| Field | Description |
|-------|-------------|
| Tower Server | The predefined Ansible Tower server to run the sync on. |
| Tower Credentials Override | Overrides the credentials from global Ansible Tower configuration. |
| Project Name | The name of the project to perform the SCM sync. |
| Revision | The revision to set for the specified project.|
| Verbose | Add additional messages to the Jenkins console about the job run.|

### Pipeline Support
Project revision can be executed from workflow scripts. The ansibleTowerProjectRevision function is made available through this plugin. The towerServer, project and revision parameters are the only ones required.
```groovy
        projectSyncResults = ansibleTowerProjectRevision(
            towerServer: 'EC2 AWX 8',
            project: 'Demo Project',
            revision: 'v1.0',
            throwExceptionWhenFail: false,
            verbose: false
        )
```

## Async Execution

As of version 0.10.0, pipeline scripts that run jobs within Tower support the async option. This will run the Tower task but immediately return the control of the job back to Jenkins. In the return value from either ansibleTower or ansibleTowerProjectSync will be an object that can be used later on to interact with the job:

```groovy
stage('Launch Tower job') {
    node('master') {
        // Here we launching the Tower job in async mode
        tower_job = ansibleTower(
                async: true,
                jobTemplate: 'Jenkins Export Vars',
                templateType: 'job',
                towerServer: 'Tower 3.6.0'
        )
        println("Tower job "+ tower_job.get("JOB_ID") +" was submitted. Job URL is: "+ tower_job.get("JOB_URL"))
    }
}
 
// Since control is returned this stage will run right away
stage('Something else') {
    node('master') {
        println("Doing something else")
    }
}
 
// Now we can use our tower_job object to wait for the Tower job to complete
stage('Wait for Tower job') {
    node('master') {
        def job = tower_job.get("job", null)
        if(job == null) {
            errir("The tower job was defined as null!")
        }
        timeout(120) {
            waitUntil {
                return job.isComplete()
            }
        }
    }
}
 
// Once the job is done we may want to do things like:
//    * Get the logs
//    * Check on exported values
//    * See if the job was successful or not
stage('Process Tower results') {
    node('master') {
        // Def a variable to save some typing
        def job = tower_job.get("job", null)
        if(job == null) {
            error("Tower job was null")
        }
 
        // First lets get and display the logs
        def Vector<String> logs = job.getLogs()
        for (String line : logs) {
            println(line)
        }
 
        // Now lets get our exports, these depend on us calling getLogs
        def HashMap<String, String> exports = job.getExports()
        def returned_value = exports.get("value", "Not Defined")
        if(returned_value != "T-REX") {
            println("Tower job did not return a T-Rex: "+ returned_value)
        } else {
            println("Exports were as expected")
        }
 
        // Finally, lets see if the job was successful
        boolean successful = job.wasSuccessful()
        if(successful) {
            println("Job ran successfully")
        } else {
            error("The job did not end well")
        }

        // Release the Tower token (see note below)
        job.releaseToken()
    }
}
```

**Note:** the above example would require in-process script approvals in order to be run. Specifically the following needed to be added:

* method java.util.Dictionary get java.lang.Object
* method org.jenkinsci.plugins.ansible_tower.util.TowerJob getExports
* method org.jenkinsci.plugins.ansible_tower.util.TowerJob getLogs
* method org.jenkinsci.plugins.ansible_tower.util.TowerJob isComplete
* method org.jenkinsci.plugins.ansible_tower.util.TowerJob wasSuccessful
* method org.jenkinsci.plugins.ansible_tower.util.TowerJob releaseToken
* new java.util.HashMap
* new java.util.Vector

Using ansibleTowerProjectSync will require similar script approvals:
* method org.jenkinsci.plugins.ansible_tower.util.TowerProjectSync getLogs
* method org.jenkinsci.plugins.ansible_tower.util.TowerProjectSync isComplete
* method org.jenkinsci.plugins.ansible_tower.util.TowerProjectSync wasSuccessful
* method org.jenkinsci.plugins.ansible_tower.util.TowerProjectSync releaseToken

Please consider if you want these options added and use at your own risk.


**Note:** with release 0.13 new auth procedures were implemented. If you are using a username/password credential a token will attempt to be retrieved when calling the Tower API. When running with the async option, the token will be released as soon as control is returned to your groovy script. If you many another call to the API (i.e. by calling getLogs) a new Token will be established. It is your responsibility to remove that token when you no longer need it. Failure to do so will leave dangling tokens in Tower.


## Expanding Env Vars

The fields passed to Tower (project, jobTemplate, extraVars, limit, jobTags, inventory, credential) can have Jenkins Env Vars placed inside of them and expanded. For example, if you had a job parameter as TAG you could expand that in the Extra Vars like this:
```yaml
---
my_var: "$TAG"
```

## Console Color

You need to install another plugin like [AnsiColor plugin](https://wiki.jenkins-ci.org/display/JENKINS/AnsiColor+Plugin) to output a colorized Ansible log.

```groovy
node {
    wrap([$class: 'AnsiColorBuildWrapper', colorMapName: "xterm"]) {
        ansibleTower(
            towerServer: 'Prod Tower',
            jobTemplate: 'Simple Test',
            importTowerLogs: true,
            inventory: 'Demo Inventory',
            jobTags: '',
            limit: '',
            removeColor: false,
            verbose: true,
            credential: '',
            extraVars: '''---
            my_var: "Jenkins Test"'''
        )
    }
}
```

If you do not have a plugin like AnsiColor or want to remove the color from the log set removeColor: true.


## Returning Data
The plugin supports sending data from Tower back to Jenkins for use in your job. For job runs, there are two methods for exporting data: Purpose Driven Logging and Setting Stats

### Purpose Driven Logging
Then, in your Tower job simply include a debugging statement like:

```yaml
    - name: Set a Jenkins variable
      debug:
        msg: "JENKINS_EXPORT VAR_NAME=value"
```
 
The Tower plugin will recognize this message and use the EnvInject plugin to add an environment variable named VAR_NAME with a value of "value" into the pipeline for consumption by downstream tasks.

### Setting Stats
Another option is to use the set_stats module in Ansible like:

```yaml
    - name: Set a jenkins variable for with stat
      set_stats:
        data:
          JENKINS_EXPORT:
            - some_name: var_value
            - some_other_var : Another value
        aggregate: yes
        per_host: no
```

The Tower plugin will look for variables under JENKINS_EXPORT and use EnvInject plugin to add an environment variables into the pipeline for consumption by downstream tasks. In the previous example two variables would be created: some_name, set to var_value; and some_other_var set to "Another value".

**Please reference set_stats documentation for usage and additional parameters, per_host and aggregate are not necessarily needed.**

The process to leverage the returned data in Jenkins depends on your job type:

### Freestyle returns
The plugin supports sending data back to Jenkins as environment variables via the [EnvInject plugin](https://wiki.jenkins.io/display/JENKINS/EnvInject+Plugin). First be sure the plugin is installed (its not a dependency for this plugin, it needs to be installed separately).

If you try to export a variable but don't have the EnvInject plugin installed the Tower plugin will let you know with a message like:

```text
    Found environment variables to inject but the EnvInject plugin was not found
```

* Important note: if you do use this plugin as a part of Jenkins Pipeline (under a script {} section) it is possible that you may not be able to access return values from Env variables. Only way to return and access values would be pipeline returns. Please see below. Reason:
source: https://plugins.jenkins.io/envinject/
```text
Even though it is possible to set up the EnvInject Job Property and build step in Pipeline, the plugin does not provide full compatibility with Jenkins Pipeline.

Supported use-cases:

    Injection of EnvVars defined in the "Properties Content" field of the Job Property
        These EnvVars are being injected to the script environment and will be inaccessible via the "env" Pipeline global variable
        Please note there is also a known compatibility issue with Durable Task Plugin 1.13

All other use-cases of the plugin may work incorrectly in Jenkins Pipeline. Please see JENKINS-42614 for more information about unsupported use-cases. There is no short-term plan to fix the compatibility issues though any pull requests to the plugin will be appreciated.
```

### Pipeline returns

When running under a pipeline, the EnvInject plugin is not required. The environment variables will either be:

* Returned in the return object (for non-async jobs)
* Accessible through the job object (for async jobs)

The async job example can be seen above in the Pipeline Support section.

For a non-async job you can access the variables as so:
```groovy
node {
    stage ('deploy gitlab') {
        wrap([$class: 'AnsiColorBuildWrapper', colorMapName: "xterm"]) {
            results = ansibleTower(
                towerServer: 'Tower 3.3.0',
                jobTemplate: 'Jenkins Export Vars',
                importTowerLogs: true,
                removeColor: false,
                verbose: true,
            )
             
            sh 'env' // this may not always work
        }
        println(results.JOB_ID)
        println(results.value)
    }
}
```

Another example could be as follows:
```grrovy
pipeline {
    agent any
    environment {
        MY_ENV_VAR='' // not recommended, only for demonstration
    }
    stages {
      stage('Tower') {
            steps{
                script{
                    results=ansibleTower(
                        importTowerLogs: true,
                        importWorkflowChildLogs: false,
                        jobTemplate: 'Demo Set Stats template',
                        jobType: 'run',
                        removeColor: false,
                        templateType: 'job',
                        throwExceptionWhenFail: false,
                        towerCredentialsId: '5a55e074-d8c3-474e-b0de-c2f3af738c3c',
                        towerServer: 'infra-ansible-tower',
                        verbose: true
                    )
                    print(results['vm1']);
                    print(results.vm2);
                    $MY_ENV_VAR=results.toString(); // not recommended
                }
            }
        }
      stage('output'){
          steps{
              print(results['vm1']);
              print(results.vm2);
              echo $MY_ENV_VAR // not recommended
          }
      }
    }
}
```

In this example you can see that we are setting `results` variable and accessing it in the stage `Tower` as well as in the following stage `output`. You may skip the definition and use of `$MY_ENV_VAR`, but it is shown here as to how you can get the data in Env var. One of the disadvantage with Env var is that if you are getting return value other than a `String` (which is the case in this plugin) you may fail to set and use the env var correctly. Hence its recommended to not use Env var method.

Above example of pipeline uses a playbook that looks like this:
```
---
- name: Set stats sample play
  hosts: localhost
  connection: local
  vars:
    vm1:
      name: Test stats
      vm_name: test-vm-1
      ip_addr: 10.x.x.x
      description: some descriptive text
    vm2:
      name: Test stats
      vm_name: test-vm-2
      ip_addr: 10.x.x.x
      description: some descriptive text
  tasks:
    - name: Set stats for demo
      set_stats:
        data:
          JENKINS_EXPORT:
            - vm1: "{{ vm1 }}"
            - vm2: "{{ vm2 }}"
```

For a job run, there are three special variables in results:

* JOB_ID - a string containing the Tower ID number of the job
* JOB_URL - a string containing the URL to the job in Tower
* JOB_RESULT - a String of either SUCCESS or FAILED depending on the job status.<br/>**Note:** This variable is only applied for a non-async job.

For a project sync, there are:
* SYNC_ID - a string containing the Tower ID number of the project sync
* SYNC_URL - a string containing the URL to the sync job in Tower
* SYNC_RESULT = a string of either SUCCESS or FAILED depending on the sync status.<br/>**Note:** This variable is only applied for a non-async job.
