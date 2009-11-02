includeTargets << new File("$cloudFoundryPluginDir/scripts/_CloudFoundryBase.groovy")


target(main:"Deletes the specified deployment") {
  deleteDeployment()
}

setDefaultTarget("main")