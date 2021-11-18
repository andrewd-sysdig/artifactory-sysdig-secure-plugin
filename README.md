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
      "token":"<sysdig-secure-token>",
      "events":[
         "docker.tagCreated"
      ],
      "repositories":[
         "*"
      ],
      "path":"*"
   },
   "baseurl":"<base.artifactory.url>",
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

`curl -XPOST -u [admin-username]:[admin-password] [artifactory-url]/api/plugins/execute/pluginReload`
