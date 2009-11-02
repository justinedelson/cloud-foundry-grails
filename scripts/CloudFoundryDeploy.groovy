includeTargets << new File("$cloudFoundryPluginDir/scripts/_CloudFoundryBase.groovy")


target(main:"Deploys the current application to Cloud Foundry using the given deployment name") {
  deployApplication()
}

setDefaultTarget("main")