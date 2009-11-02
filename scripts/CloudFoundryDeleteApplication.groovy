includeTargets << new File("$cloudFoundryPluginDir/scripts/_CloudFoundryBase.groovy")


target(main:"Deletes the specified application") {
  deleteApplication()
}

setDefaultTarget("main")