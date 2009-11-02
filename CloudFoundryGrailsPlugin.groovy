class CloudFoundryGrailsPlugin {
    // the plugin version
    def version = "0.1"
    // the version or versions of Grails the plugin is designed for
    def grailsVersion = "1.1 > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
            "grails-app/views/error.gsp",
            "grails-app/conf/CloudFoundry.groovy"
    ]

    def scopes = [excludes:'war']
  
    def author = "Graeme Rocher"
    def authorEmail = "graeme.rocher@springsource.com"
    def title = "Cloud Foundry Plugin for Grails"
    def description = '''\\
Integrates Grails with Cloud Foundry, SpringSource's cloud hosting environment
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/cloud-foundry"

}
