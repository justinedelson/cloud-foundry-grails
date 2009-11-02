includeTargets << new File("$cloudFoundryPluginDir/scripts/_CloudFoundryBase.groovy")


target(main:"Provides Cloud Foundry Status Information") {
  depends(parseArguments)

  def params = argsMap.params
  def firstArg = params ? params[0]?.trim() : null
  if(params.size()>1) {
    argsMap.params = params[1..-1]
  }

  switch(firstArg) {
    case 'upload':
        uploadApplication()
    break
    case 'deploy':
       deployApplication()
    break
    case 'stop':
       stopDeployment()
    break
    case 'info':
       showDeploymentInfo()
    break
    case 'delete':
       deleteDeployment()
    break
    case 'redeploy':
        redeploy()
    break
    default:
      listApplications()
      listDeployments()

  }

}

setDefaultTarget("main")

