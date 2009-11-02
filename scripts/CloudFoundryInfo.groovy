includeTargets << new File("$cloudFoundryPluginDir/scripts/_CloudFoundryBase.groovy")


target(main:"Lists the known CloudFoundry applications") {
  showDeploymentInfo()
}

setDefaultTarget("main")

