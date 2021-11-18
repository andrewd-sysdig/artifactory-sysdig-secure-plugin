Sysdig Artifactory Plugin
===============================

Components
-----------------
1. SysdigSecurePlugin.groovy - main script, modify only if needing to change functionality
2. SysdigSecurePlugin.properties - used to configure the plugin for your environment

Installation
-----------------
1. Configure and copy SysdigSecurePlugin.properties to ARTIFACTORY_HOME/etc/plugins
2. Copy SysdigSecurePlugin.groovy to ARTIFACTORY_HOME/etc/plugins

Configuration
-----------------
Configuration for the plugin is through SysdigSecurePlugin.properties which has various environment specific and global options.

```json
{
	"sysdig":{
		"url":"http<s>://<fqdn/ip>:<port>",
		"token": "<sysdig-secure-token>",
		"events":[
			"docker.tagCreated"
		],
    "repositories": [
      "*"
    ],
    "path": "*"
	},
	"baseurl": "<base.artifactory.url>",
	"debug":false,
	"timeout":15000
}
```

### Webhook properties
| Property      | Description   | Required  | Default |
| ------------- |-------------| ---------| -------|
| sysdig.url     | The URL to send images for scanning | true      | -       |
| sysdig.token   | Sysdig auth token | true      | -       |
| events      | The events to listen to      | true      | -       |
| repositories | List of repositories to target for scanning. | False     | * (all repositories)   |
| path | A path that must match for scanning to be triggered. Accepts wildcard '*'. Do not include repository name in path. | False     | * (all paths)   |

### Global properties
| Property      | Description   | Required  | Default |
| ------------- | ------------- | --------- | ------- |
| debug     | Additional logging | false      | false       |
| timeout      | Timeout for POST call      | false      | 15000 (ms)  |
| baseurl      | Base URL of Artifactory instance. | false      | -  |

#### Making changes to the configuration

You can make changes to the configuration without having to reload the plugin by making the following request:

`curl -XPOST -u [admin-username]:[admin-password] [artifactory-url]/api/plugins/execute/webhookReload`


#### Detailed Sample Configuration
Here is an example of configuration file using all the bells and whistles:

```json
{
  "webhooks": {
      "slack": {
        "url": "https://hooks.slack.com/services/######/######/#######",
        "events": [
          "execute.pingWebhook",
          "docker.tagCreated",
          "docker.tagDeleted"
        ],
        "format": "slack"
    },
    "docker": {
      "url": "http://example2.com",
      "events": [
        "docker.tagCreated",
        "docker.tagDeleted"
      ],
      "repositories": [
        "docker-local"
      ],
      "path": "example:1.0.1"
    },
    "audit": {
      "url": "http://example3.com",
      "events": [
        "storage.afterCreate",
        "storage.afterDelete",
        "storage.afterPropertyCreate"
      ],
      "async": true,
      "repositories": [
        "generic-local"
      ],
      "path": "archive/contrib/*"
    },
    "allStorageAndReplication": {
      "url": "http://example4.com",
      "events": [
        "storage.afterCreate",
        "storage.afterDelete",
        "storage.afterMove",
        "storage.afterCopy",
        "storage.afterPropertyCreate",
        "storage.afterPropertyDelete"
      ],
      "enabled": false
    }
  },
  "debug": false,
  "timeout": 15000
}
```

Supported Events
-----------------

### Storage
* storage.afterCreate - Called after artifact creation operation
* storage.afterDelete - Called after artifact deletion operation
* storage.afterMove - Called after artifact move operation
* storage.afterCopy - Called after artifact copy operation
* storage.afterPropertyCreate - Called after property create operation
* storage.afterPropertyDelete - Called after property delete operation

### Build
* build.afterSave - Called after a build is deployed

### Execute
* execute.pingWebhook - Simple test call used to ping a webhook endpoint

### Docker
* docker.tagCreated - Called after a tag is created
* docker.tagDeleted - Called after a tag is deleted

Webhook Formatters
-----------------

* default - The default formatter
* keel - A POST formatted specifically for keel.sh
* slack - A POST formatted specifically for Slack
* spinnaker - A POST formatted specifically for Spinnaker

### Using the keel format

In order to work with keel format, you need to set your docker registry url in your config file
( to be prepended to the image name + tag in the webhook)

```json
{
  "webhooks": {
    "keel": {
        "url": "https://keel.example.com/v1/webhooks/native",
        "events": [
          "docker.tagCreated"
        ],
        "format": "keel"
    }
  },
  "dockerRegistryUrl": "docker-registry.example.com"
}

```

#### Using the slack format

In order to work with Slack POST hooks, you need to add the [incoming-webhook app](https://api.slack.com/incoming-webhooks).
This will generate a look with the format 'https://hooks.slack.com/services/######/######/#######' which you will use as
the **url** in the configuration file. See the detailed sample configuration above.

#### Using the spinnaker format

In order to work with Spinnaker POST hooks, you need to enable spinnaker support and set the base url.
This will generate a look with the format 'https://www.spinnaker.io/reference/artifacts/#format' which you will use as
the **url** in the configuration file. See the detailed sample configuration below.

```json
{
  "webhooks": {
    "helm": {
      "url": "SPINNAKER WEBHOOK URL for HELM",
      "events": [
        "storage.afterCreate"
      ],
      "path": "*.tgz",
      "format": "spinnaker"
    },
    "docker": {
      "url": "SPINNAKER WEBHOOK URL for Docker",
      "events": [
        "docker.tagCreated"
      ],
      "format": "spinnaker"
    }
  },
  "debug": false,
  "timeout": 15000,
  "baseurl": "Artifactory base URL -- http://localhost:8081/artifactory"
}
```



Copyright &copy; 2011-, JFrog Ltd.
