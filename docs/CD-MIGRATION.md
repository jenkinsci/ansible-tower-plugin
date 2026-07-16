# Jenkins Plugin Continuous Delivery Migration

This repository is prepared for Jenkins Plugin Continuous Delivery (JEP-229). The CD workflow is the official Jenkins reusable workflow and releases the Jenkins `hpi` artifact; it is not a general-purpose application deployment pipeline.

## Before CD

- Releases used manually selected versions such as `0.18.1` and the Maven release process.
- A maintainer needed local release credentials and performed the release from a checkout.
- The repository had no Maven Incrementals extension or Jenkins CD workflow.

## After CD

- The POM version is `${revision}${changelist}`. Local builds use `0.18.1-SNAPSHOT`.
- Release builds replace the changelist using Git history and the commit hash. With the retained `0.18.1` revision prefix, CD versions have the form `0.18.1NNN.vHASH` because the required POM expression has no separator beyond the changelist value supplied by the workflow.
- The official Jenkins reusable CD workflow deploys the plugin to Jenkins Artifactory and creates the GitHub release and generated release notes.
- `maven-release-plugin` is not used.

## How releases happen

The unchanged official workflow listens for a completed Jenkins check on the default branch. After a successful `ci.jenkins.io` build, it releases when there is a merged pull request with an interesting Jenkins release-drafter label. Pull request titles and labels determine the generated release notes.

Maintainers can also open **Actions → cd → Run workflow** on the default branch. The current commit must already have a successful Jenkins check. Leave **validate_only** unchecked to deploy; check it to validate and draft release notes without running the release job.

## Version numbers

Do not set a release version or run Maven release goals. Keep `revision` as the manually controlled compatibility prefix and `changelist` as `-SNAPSHOT` for local development. JEP-229 supplies a changelist derived from commit count and Git hash for releases. Any future change to `revision` must preserve Maven/update-center ordering and should be reviewed deliberately.

## Required setup and rollout

1. Open this plugin change as a pull request and request Jenkins hosting-team review before merging it.
2. In a separate clone/fork of `jenkins-infra/repository-permissions-updater`, add `cd.enabled: true` to `permissions/plugin-ansible-tower.yml` and link the plugin pull request.
3. Merge the permissions PR and wait for `MAVEN_USERNAME` and `MAVEN_TOKEN` to appear as Actions repository secrets.
4. Review Jenkins organization commit access and deploy keys because any committer able to merge can cause a release.
5. Apply an appropriate predefined release-drafter label to this and later pull requests before merging.
6. Merge the plugin pull request only after hosting-team approval and the required secrets are available.

## Rollback considerations

- Stop automatic releases by disabling the workflow or removing its `check_run` trigger in a reviewed pull request. Existing releases in Artifactory and GitHub are immutable and must not be overwritten.
- Revert faulty code with a new pull request and publish a newer corrective CD release; do not delete or replace an already published artifact.
- JEP-229 permission is exclusive by default. Restoring manual publishing requires a separately reviewed repository-permissions-updater change (for example, disabling CD or setting `exclusive: false`) and re-establishing authorized release credentials.
- Do not return to a lower version sequence. Any future version format must compare newer than every version already published to the Jenkins update center.
