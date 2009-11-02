includeTargets << new File("$cloudFoundryPluginDir/scripts/_CloudFoundryBase.groovy")


target(main:"Stops the specified deployment") {
  stopDeployment()
}

setDefaultTarget("main")