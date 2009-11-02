includeTargets << new File("$cloudFoundryPluginDir/scripts/_CloudFoundryBase.groovy")

scriptEnv = "production"

target (main: "Uploads a Grails application to CloudFoundry") {
  uploadApplication()
}
setDefaultTarget("main")

