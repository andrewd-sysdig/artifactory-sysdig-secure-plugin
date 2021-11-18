/*
 * Copyright (C) 2021 Sysdig Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.google.common.collect.BiMap
import com.google.common.collect.ImmutableBiMap
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import java.util.regex.Pattern
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
/**
 * Sysdig Plugin for Artifactory
 *
 * This webhook includes the following components
 * 1. SysdigSecurePlugin.groovy - main script, modify only if needing to change functionality
 * 2. SysdigSecurePlugin.properties - used to configure the plugin for your environment
 *
 * Installation:
 * 1. Configure and copy SysdigSecurePlugin.properties to ARTIFACTORY_HOME/etc/plugins
 * 2. Copy SysdigSecurePlugin.groovy to ARTIFACTORY_HOME/etc/plugins
 *
 */

class Globals {

    static final SUPPORT_MATRIX = [
        "storage": [
            "afterCreate": [ name: "storage.afterCreate", description: "Called after artifact creation operation",
                             humanName: "Artifact created"]
        ],
        "execute": [
            "pingSysdig": [ name: "execute.pingSysdig",
                             description: "Test connection to Sysdig Scanning Backend",
                             humanName: "Sysdig ping test"]
        ],
        "docker": [
            "tagCreated": [ name: "docker.tagCreated", description: "Called after a tag is created",
                            humanName: "Docker tag created"]
        ]
    ]

    static final RESPONSE_FORMATTER_MATRIX = [
        default: [
            description: "Sysdig SaaS default formatter", formatter: new ResponseFormatter ( )
        ]
    ]

    static SUPPORTED_EVENTS = [ ].toSet ( )
    static {
        SUPPORT_MATRIX.each {
            k, v ->
                v.each { k1, v1 ->
                    SUPPORTED_EVENTS.add(v1.name)
                }
        }
    }

    static def eventToSupported(String event) {
        def idx = event.indexOf('.')
        return SUPPORT_MATRIX[event.substring(0, idx)][event.substring(idx + 1)]
    }
    enum PackageTypeEnum {
        DOCKER ("docker/image")

        private String value

        PackageTypeEnum(String value) {
            this.value = value
        }

        String toString() {
            return value;
        }
    }
    static final BiMap<String, PackageTypeEnum> PACKAGE_TYPE_MAP = new ImmutableBiMap.Builder<String, PackageTypeEnum>()
            .put("docker", PackageTypeEnum.DOCKER)
            .build()
    static repositories
}

SysdigPlugin.init(ctx, log)

executions {

    Globals.repositories = repositories

    pingSysdig(httpMethod: 'GET') {
        def json = new JsonBuilder()
        json (
                message: "It works!",
        )
        hook(Globals.SUPPORT_MATRIX.execute.pingSysdig.name, json)
    }

    pluginReload (httpMethod: 'POST') {
        SysdigPlugin.reload()
        message = "Reloaded!\n"
    }

    pluginInfo(httpMethod: 'GET') {
        def sb = ''<<''
        sb <<= 'Sysdig Artifactory Plugin Information\n'
        sb <<= '-----------------\n\n'
        Globals.SUPPORT_MATRIX.each { k, v ->
            sb <<= '### ' << k.capitalize() << '\n'
            v.each { k1, v1 ->
                sb <<= "${v1.name} - ${v1.description}\n"
            }
            sb <<= '\n'
        }
        sb <<= '\n\nSysdig Artifactory Plugin Formatters\n'
        sb <<= '-----------------\n\n'
        Globals.RESPONSE_FORMATTER_MATRIX.each { k, v ->
            sb <<= "${k} - ${v.description}\n"
        }
        sb <<= '\n'
        message = sb.toString()
    }

}

storage {
    afterCreate { item ->
        hook(Globals.SUPPORT_MATRIX.storage.afterCreate.name, item ? new JsonBuilder(item) : null)
    }
}

class ResponseFormatter {
    def format(String event, JsonBuilder data) {
        def eventTypeMetadata = Globals.eventToSupported(event)
        def builder = new JsonBuilder()
        def json = data.content

        if (Globals.SUPPORT_MATRIX.docker.tagCreated.name == eventTypeMetadata.name) {
            builder {
                tag "${SysdigPlugin.baseUrl()}/${json.docker.image}:${json.docker.tag}"
                annotations(
                    "added-by": "cicd-scan-request",
                )
            }
        } else{
            builder {
                text "Artifactory: ${eventTypeMetadata['humanName']} event is not supported by Sysdig formatter"
            }
        }
        return builder
    }
}

def dockerDataDecorator(JsonBuilder data) {
    def tagName = null, imageName = null
    def path = data.content.relPath
    def m = path =~ /^(.*)\/(.*?)\/manifest.json$/
    if (m[0] && m[0].size() == 3)
    {
        imageName = m[0][1]
        tagName = m[0][2]
    }
    def builder = new JsonBuilder()
    builder {
        docker(
            [
                "tag": tagName,
                "image": imageName
            ]
        )
        event data.content
    }
    return builder

}

def dockerEventDecoratorWork(String event, JsonBuilder data) {
    def json = data.content
    def repoKey = json.repoKey
    def repoInfo = repositories.getRepositoryConfiguration(repoKey)
    if (repoInfo && repoInfo.isEnableDockerSupport())
        if (json.name && json.name == "manifest.json")
            hook(event, dockerDataDecorator(data))
}

def dockerEventDecorator(String event, JsonBuilder data) {
    // New tag creation
    if (event == Globals.SUPPORT_MATRIX.storage.afterCreate.name)
        dockerEventDecoratorWork(Globals.SUPPORT_MATRIX.docker.tagCreated.name, data)
    // Tag deletion
    if (event == Globals.SUPPORT_MATRIX.storage.afterDelete.name)
        dockerEventDecoratorWork(Globals.SUPPORT_MATRIX.docker.tagDeleted.name, data)
}

def hook(String event, JsonBuilder data) {
    try {
        if (SysdigPlugin.failedToLoadConfig) {
            log.error("Failed to load configuration from webhook.config.json. Verify that it is valid JSON.")
            return
        }
        if (Globals.SUPPORTED_EVENTS.contains(event) && SysdigPlugin.active(event)) {
            log.trace(data.toString())
            log.info("Plugin being triggered for event '${event}'")
            SysdigPlugin.run(event, data)
        }
        // Docker decorator should occur after the basic event  even if we don't care about the basic event
        dockerEventDecorator(event, data)
    } catch (Exception ex) {
        // Don't risk failing the event by throwing an exception
        if (SysdigPlugin.debug())
            ex.printStackTrace()
        log.error("Plugin threw an exception: " + ex.getMessage())
    }
}

class SysdigPlugin {
    private static SysdigPlugin p
    private static final int MAX_TIMEOUT = 60000
    private static final String REPO_KEY_NAME = "repoKey"
    private static final responseFormatters = Globals.RESPONSE_FORMATTER_MATRIX
    public static boolean failedToLoadConfig = false
    def triggers = new HashMap()
    def debug = false
    def connectionTimeout = 15000
    def baseUrl
    def ctx = null
    def log = null

    ExecutorService excutorService = Executors.newFixedThreadPool(10)

    static void run(String event, Object json) {
        p.process(event, json)
    }

    static boolean active(String event) {
        if (p.triggers.get(event)) {
            return true
        } else {
            return false
        }
    }

    static boolean debug() {
        return p != null && p.debug == true
    }

    static String baseUrl() {
        return p.baseUrl
    }

    private String getFormattedJSONString(JsonBuilder json, String event, PluginEndpointDetails webhook) {
        if (webhook.isDefaultFormat() || !responseFormatters.containsKey(webhook.format)) {
            return (responseFormatters['default'].formatter.format(event, json)).toString()
        }
        return (responseFormatters[webhook.format].formatter.format(event, json)).toString()
    }

    private void process(String event, Object json) {
        if (active(event)) {
            def webhookListeners = triggers.get(event)
            if (webhookListeners) {
                // We need to do this twice to do all async first
                for (PluginEndpointDetails webhookListener : webhookListeners) {
                    try {
                        if (webhookListener.isAsync()) {
                            if (eventPassedFilters(event, json, webhookListener))
                                excutorService.execute(
                                        new PostTask(webhookListener.url, getFormattedJSONString(json, event, webhookListener)))
                        }
                    } catch (Exception e) {
                        // We don't capture async results
                        if (debug)
                            e.printStackTrace()
                    }
                }
                for (def webhookListener : webhookListeners) {
                    try {
                        if (!webhookListener.isAsync())
                            if (eventPassedFilters(event, json, webhookListener))
                                callPost(webhookListener.url, getFormattedJSONString(json, event, webhookListener))
                    } catch (Exception e) {
                        if (debug)
                            e.printStackTrace()
                    }
                }
            }
        }
    }

    private boolean eventPassedFilters(String event, Object json, PluginEndpointDetails webhook) {
        return eventPassedDirectoryFilter(event, json) && eventPassedRepositoryAndPathFilter(event, json, webhook)
    }

    private boolean eventPassedRepositoryAndPathFilter(String event, Object json, PluginEndpointDetails webhook) {
        boolean passesFilter = true
        def jsonData = null // Don't slurp unless necessary to avoid overhead
        if (event.startsWith("storage") || event.startsWith("docker")) {
            // Check repo if needed
            if (!webhook.allRepos()) {
                jsonData = new JsonSlurper().parseText(json.toString())
                def reposInEvent = findDeep(jsonData, REPO_KEY_NAME)
                boolean found = false
                if (reposInEvent != null && reposInEvent.size() > 0) {
                    reposInEvent.each {
                        if (webhook.appliesToRepo(it))
                            found = true
                    }
                }
                passesFilter = found
            }
            if (passesFilter && webhook.hasPathFilter()) { // Check path if needed
                if (jsonData == null)
                    jsonData = new JsonSlurper().parseText(json.toString())
                def matches = false
                if (event.startsWith("docker")) {
                    matches = webhook.matchesPathFilter(jsonData.docker.image) &&
                        webhook.matchesTagFilter(jsonData.docker.tag)
                } else if (event.startsWith("storage")) {
                    if (Globals.SUPPORT_MATRIX.storage.afterMove.name == event ||
                            Globals.SUPPORT_MATRIX.storage.afterCopy.name == event) {
                        matches = webhook.matchesPathFilter(jsonData.item.relPath) ||
                                webhook.matchesPathFilter(jsonData.targetRepoPath.relPath)
                    } else if (Globals.SUPPORT_MATRIX.storage.afterPropertyCreate.name == event ||
                            Globals.SUPPORT_MATRIX.storage.afterPropertyDelete.name == event) {
                        matches =  webhook.matchesPathFilter(jsonData.item.relPath)
                    } else {
                        matches =  webhook.matchesPathFilter(jsonData.relPath)
                    }
                }
                passesFilter = matches
            }
        }
        return passesFilter
    }

    private boolean eventPassedDirectoryFilter(String event, Object object) {
        if (event.startsWith("storage")) {
            def json = object.content
            if (json.folder)
                return false
        }
        return true
    }

    private String callPost(String urlString, String content) {
        def url = new URL(urlString)
        def post = url.openConnection()
        post.method = "POST"
        post.doOutput = true
        post.setConnectTimeout(connectionTimeout)
        post.setReadTimeout(connectionTimeout)
        post.setRequestProperty("Content-Type", "application/json")
        post.setRequestProperty("Authorization", "Bearer bc319b9c-34a5-4dea-b4c6-cab8792344e0")
        def writer = null, reader = null

        try {
            writer = post.outputStream
            writer.write(content.getBytes("UTF-8"))
            writer.flush()
            def postRC = post.getResponseCode()
            def response = postRC
            if (postRC.equals(200)) {
                reader = post.inputStream
                response = reader.text
            }
            return response
        } finally {
            if (writer != null)
                writer.close()
            if (reader != null)
                reader.close()
        }
    }

    static void reload() {
        def tctx = null
        def tlog = null
        if (p != null) {
            tctx = p.ctx
            tlog = p.log
            p.excutorService.shutdown()
            p = null
        }
        init(tctx, tlog)
    }

    synchronized static void init(ctx, log) {
        if (p == null) {
            p = new SysdigPlugin()
            p.ctx = ctx
            p.log = log
            failedToLoadConfig = false
            try {
                p.loadConfig()
            } catch (ex) {
                failedToLoadConfig = true
                p = null
            }
        }
    }

    private void loadConfig() {
        final String CONFIG_FILE_PATH = "${ctx.artifactoryHome.etcDir}/plugins/SysdigSecurePlugin.properties"
        def inputFile = new File(CONFIG_FILE_PATH)
        def config = new JsonSlurper().parseText(inputFile.text)
        if (config.sysdig) {
            loadPlugin(config.sysdig)
            // Potential debug flag
            if (config.containsKey("debug"))
                p.debug = config.debug == true
            // Timeout
            if (config.containsKey("timeout") && config.timeout > 0 && config.timeout <= MAX_TIMEOUT)
                p.connectionTimeout = config.timeout
            // BaseUrl
            if (config.containsKey("baseurl"))
                p.baseUrl = config.baseurl
        }
    }

    private void loadPlugin(Object cfg) {
        if (cfg.url) {
            if (cfg.events) {
                // Registry the plugin details with an event (or set of events)
                def sysdigDetails = new PluginEndpointDetails()
                // Async flag
                if (cfg.containsKey('async'))
                    sysdigDetails.async = cfg.async
                // Repositories
                if (cfg.containsKey('repositories'))
                    sysdigDetails.repositories = cfg.repositories
                // Path filter
                if (cfg.containsKey('path'))
                    sysdigDetails.setPathFilter(cfg.path)
                // Events
                cfg.events.each {
                    sysdigDetails.url = "${cfg.url}/api/scanning/v1/anchore/images"
                    sysdigDetails.token = cfg.token
                    addEvent(it, sysdigDetails)
                }
            }
        }
    }

    private void addEvent(String event, PluginEndpointDetails sysdigDetails) {
        def eventHooks = p.triggers.get(event)
        if (!eventHooks)
            p.triggers.put(event, [sysdigDetails])
        else
            eventHooks.add(sysdigDetails)
    }

    def findDeep(def tree, String key) {
        Set results = []
        findDeep(tree, key, results)
        return results
    }

    def findDeep(def tree, String key, def results) {
        switch (tree) {
            case Map: tree.findAll { k, v ->
                if (v instanceof Map || v instanceof Collection)
                    findDeep(v, key, results)
                else if (k == key)
                    results.add(v)
            }
            case Collection:  tree.findAll { e ->
                if (e instanceof Map || e instanceof Collection)
                    findDeep(e, key, results)
            }
        }
    }

    class PluginEndpointDetails {
        public static String ALL_REPOS = "*"
        def url
        def token
        def format = null // Default format
        def repositories = [ALL_REPOS] // All
        def async = true
        Pattern path = null
        String tag = null

        boolean allRepos() {
            return repositories.contains(ALL_REPOS)
        }

        boolean appliesToRepo(String repoKey) {
            return allRepos() || repositories.contains(repoKey)
        }

        boolean isAsync() {
            return async
        }

        boolean isDefaultFormat() {
            return format == 'default'
        }

        boolean hasPathFilter() {
            return path != null
        }

        boolean matchesTagFilter(String actualTag) {
            if(tag == null)
                return true
            return tag.equals(actualTag)
        }

        boolean matchesPathFilter(String actualPath) {
            if (path == null)
                return true
            return path.matcher(actualPath).matches()
        }

        void setPathFilter(String searchString) {
            // Remove leading '/'
            if (searchString.startsWith('/'))
                searchString = searchString.substring(1)
            //Set tag if one exists
            def tagIndex = searchString.indexOf(':')
            if (tagIndex >= 0 && tagIndex < searchString.length()-1) {
                tag = searchString.substring(tagIndex+1)
                searchString = searchString.substring(0, tagIndex)
            }
            path = Pattern.compile(regexFilter(searchString))
        }

        private String regexFilter(String searchString) {
            (['\\','.','[',']','{','}','(',')','<','>','+','-','=','?','^','$', '|']).each {
                searchString = searchString.replace(it, '\\' + it)
            }
            searchString = searchString.replace('*', '.*')
            return searchString
        }
    }

    class PostTask implements Runnable {
        private String url
        private String content

        PostTask(String url, String content) {
            this.url = url
            this.content = content
        }

        void run() {
            callPost(url, content)
        }
    }
}