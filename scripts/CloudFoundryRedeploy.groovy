includeTargets << new File("$cloudFoundryPluginDir/scripts/_CloudFoundryBase.groovy")


target(main:"Redeploys the specified deployment") {
  redeploy()
}

setDefaultTarget("main")