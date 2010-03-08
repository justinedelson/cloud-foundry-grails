import net.chrisrichardson.client.CloudFoundryClient
import net.chrisrichardson.client.dto.DeploymentDto
import net.chrisrichardson.client.dto.ApplicationInfo
import net.chrisrichardson.client.dto.ApplicationDTO
import net.chrisrichardson.client.dto.OperationResponseWithId
import net.chrisrichardson.client.dto.DeploymentInfo
import java.text.DecimalFormat
import net.chrisrichardson.client.dto.DeploymentDetailsDto
import net.chrisrichardson.client.dto.TierDetails
import net.chrisrichardson.client.dto.InstanceDetails
import java.util.concurrent.Executor
import java.util.concurrent.FutureTask
import java.util.concurrent.Callable

scriptEnv = "production"

includeTargets << grailsScript("_GrailsInit")

/**
 * Parses the basic cloud foundry settings and creates a client
 */
target(parseCloudFoundrySettings:"Parses CloudFoundry settings") {
  depends(parseArguments)
  if(!binding.variables.containsKey('cloudFoundryClient')) {

    final File projectFile = new File("${basedir}/grails-app/conf/CloudFoundry.groovy")
    def buildConfig = grailsSettings.config
    if(projectFile.exists()) {
      buildConfig.merge(configSlurper.parse(projectFile.toURL()))
    }
    cloudFoundryConf = buildConfig
    dataSourceConf = configSlurper.parse(new File("${basedir}/grails-app/conf/DataSource.groovy").toURL())

    final String username = cloudFoundryConf.cloudFoundry.username ?: ""
    final String password = cloudFoundryConf.cloudFoundry.password ?: ""

    if(argsMap.debug) {
      println "Using Cloud Foundry credentials: [username: $username, password: $password]"
    }
    if(argsMap.testServer) {
      cloudFoundryClient = new CloudFoundryClient(username,
                                                  password,
                                                  "ec2-174-129-164-180.compute-1.amazonaws.com", 80, "cfapp", false)
    }
    else {
      cloudFoundryClient = CloudFoundryClient.makeCloudFoundryDotComClient(username, password)
    }
  }
}

/**
 * Lists all known deployments by printing them to the console
 */
target(listDeployments:"Lists all known deployments") {
  depends(parseCloudFoundrySettings)
  CloudFoundryClient client = cloudFoundryClient
  def (deployments, error) = getDeployments(client)
  println()
  if(deployments) {
    println "Cloud Foundry Deployments"
    println "------------------------------------------------"
    for(DeploymentDto deployment in deployments) {
       println " - Id: ${deployment.id}, Name: ${deployment.name}, Application:${deployment.applicationName}, State: ${deployment.state}, Health: ${deployment.health}"
    }
  }
  else {

    println "No Cloud Foundry deployments found"
    if(error)
      println "An error occured: ${error}"
  }
}

/**
 * Lists all known applications by printing them to the console
 */
target(listApplications:"Lists all known applications") {
   depends(parseCloudFoundrySettings)

  CloudFoundryClient client = cloudFoundryClient
  def (applications, error) = getApplications(client)

  println()
  if(applications) {
    println "Cloud Foundry Applications"
    println "------------------------------------------------"
    for(ApplicationDTO app in applications) {
       println "- Id: ${app.id}, Name: ${app.name}, Deployed: ${app.deployed}, Region: ${app.region}"
    }
  }
  else {
    println "No Cloud Foundry applications found"
    if(error) {
      println "An error occurred: ${error}"
    }
  }
}

/**
 * Uploads the current Grails application to Cloud Foundry
 */
target(uploadApplication: "Uploads the current Grails application to Cloud Foundry") {
  depends(parseCloudFoundrySettings)

  CloudFoundryClient client = cloudFoundryClient
  def app = grailsAppName
  def ver = grailsAppVersion


  uploadApplicationInternal(client, app, ver)

}

/**
 * Deploys the current application to the specified Cloud Foundry deployment name
 */
target(deployApplication:"Deploys the current Grails application to the given deployment name") {
  depends(parseCloudFoundrySettings)

  CloudFoundryClient client = cloudFoundryClient
  def app = grailsAppName
  def ver = grailsAppVersion

  def (applications, error) = getApplications(client)
  if(error) {
    println "Error listing Cloud Foundry applications: $error"
    exit 1
  }
  else {
    ApplicationDTO uploadedApp = applications.find { ApplicationDTO adto -> adto.name == "$app-$ver" }

    def uploadId=uploadedApp?.id ?: 0
    if(uploadedApp) {
      println "Updating existing application..."
    }
    def response = uploadApplicationInternal(client, app, ver)
    uploadId = response.id

    if(uploadId>0) {
      try {
        def details = client.getApplicationDetails(uploadId)
        def sleepCount = 0
        print "Waiting for application [$app] to be deployable..."
        while(!details.isReadyToDeploy()) {
          sleepCount+=2000
          sleep 2000
          print "${Math.round(sleepCount/1000)}s.."
          if(sleepCount>60000) {
            println "Failed to deploy. Timeout waiting for application to be deployable reached"
            exit 1
          }
          details = client.getApplicationDetails(uploadId)
        }
      } catch (e) {
        println "Error obtaining deployment status: ${handleError(e)}"
      }
      println "Ready."

      Long deploymentId = getDeploymentIdOr { null }

      // following closure encapsulates the code to start a fresh deployment
      def startFreshDeployment = {
        def depInfo = new DeploymentInfo(applicationId:uploadId,
                                         deploymentName:"$app-$ver",
                                         region: getConfiguredRegion())


        println "Deploying application ${app}. Please wait..."
        try {
          response = client.deployApplication(depInfo)
          if(response.id > 0) {
            metadata['cloudfoundry.deployment'] = response.id.toString()
            metadata.persist()
            println "Application successfully deployed with id: ${response.id}"
            showDeploymentInfo()
          }
          else {
            println """
  Application deployment rejected.
  You may want to verify that the application to be deployed is in a deployable state."""
          }

        } catch (e) {
          println "Error deploying application: ${handleError(e)}"
          exit 1
        }
        return null
      }

      // get an existing deployment or start a fresh one
      def deployment = getDeploymentOr(client, deploymentId, startFreshDeployment)

      if(deployment) {
        if(deployment.state == 'LAUNCHED' && deployment.applicationId == uploadId) {
          try {
            println "Redeploying application [$app-$ver] with id: ${uploadId}"
            client.redeploy(deployment.id)
          } catch (e) {
            println "Error redeploying application: ${handleError(e)}"
            exit 1
          }
        }
        else {
          startFreshDeployment()
        }

      }

    }
    else {
      println """
Application rejected during upload process. The most common cause of this is a misconfigured DataSource.
Please verify your DataSource is configured appropriately and try again. For example:

-------------------------------
	production {
		dataSource {
			dbCreate = "update"
            username = "$app-user"
            password = "$app-pass"
            url = "jdbc:mysql://dbmaster/$app"
		}
	}
-------------------------------
"""
      exit 1
    }
  }


}

/**
 * Redeploys the specified deployment name
 */
target(redeploy:"Redeploys the specified Cloud Foundry deployment name") {
  depends(parseCloudFoundrySettings)

  CloudFoundryClient client = cloudFoundryClient
  withSpecifiedDeployment("Redeploying") { DeploymentDto dep ->
     client.redeploy dep.id
  }

}
/**
 * Stops the specified deployment
 */
target(stopDeployment:"Stops the specified Cloud Foundry deployment") {
   depends(parseCloudFoundrySettings)

   CloudFoundryClient client = cloudFoundryClient
   withSpecifiedDeployment("Stopping") { DeploymentDto dep ->
      client.stopDeployment dep.id
   }
}

/**
 * Deletes the specified deployment
 */
target(deleteDeployment:"Deletes the specified Cloud Foundry deployment") {
   depends(parseCloudFoundrySettings)

   CloudFoundryClient client = cloudFoundryClient
   withSpecifiedDeployment("Deleting") { DeploymentDto dep ->
      if(dep.state != 'STOPPED') {
        println "Cannot delete deployment, it has not been stopped! Run 'grails cloud-foundry-stop' first"
      }
      else {
        client.deleteDeployment dep.id
      }
   }
}

/**
 * Deletes an application from Cloud Foundry
 */
target(deleteApplication:"Deletes the sepcified Cloud Foundry application") {
  depends(parseCloudFoundrySettings)

  CloudFoundryClient client = cloudFoundryClient

  def id = getApplicationIdentifierOrExit()

  try {

    print "Deleting application..."
    client.deleteApplication id
    println "Done."
  } catch (e) {
    println "Error deleting application ${handleError(e)}"
  }


}

target(showDeploymentInfo:"Shows a deployments detailed information") {
   depends(parseCloudFoundrySettings)

   CloudFoundryClient client = cloudFoundryClient
   def id = getDeploymentIdOrExit()

   try {
     DeploymentDetailsDto details = client.getDeploymentDetails(id)

     println()
     println "Cloud Foundry Deployment Details"
     println "---------------------------------"
     println "Id: ${details.id}"
     println "Name: ${details.name}"
     println "State: ${details.state}"
     println "Creation Time: ${details.creationTime}"
     final TierDetails tier = details.webServerTier
     if(details.state == 'LAUNCHED') {

       println "Health: ${tier.health}"
       println "Instances:"
       for(InstanceDetails i in tier.instances) {
         println " - Id: ${i.id}"
         println " - Public DNS: ${i.publicDnsName}"
         println " - Home Page: http://${i.publicDnsName}/${details.contexts[0]}"
       }
     }
     println "---------------------------------"
   } catch (e) {
     println "Error obtaining deployment details: ${handleError(e)}"
   }
}

/**
 * Executes the given closure, passing the DeploymentDto instance and exits gracefully if an error occurs
 */
private withSpecifiedDeployment(String activityName, Closure c) {
  CloudFoundryClient client = cloudFoundryClient
  def deploymentId = getDeploymentIdOrExit()
  DeploymentDto dep = getDeploymentOrExit(client, deploymentId)
  try {
    print "$activityName [${dep.name}]. Please wait..."
    c.call(dep)
    println "Done."
  } catch (e) {
    println "Error $activityName: ${handleError(e)}"
    exit 1
  }
}

/**
 * Gets an application identifier or exits if none can be obtained
 */
private Long getApplicationIdentifierOrExit () {
  def error
  Long id
  try {
    id = argsMap.params ? argsMap.params[0]?.toLong() : null
    if (!id) {
      error = "Application identifer not specified"
    }
  } catch (e) {
    error = "Invalid application identifer: ${handleError(e)}"
  }

  if (error) {
    println error
    exit 1
  }
  return id
}

/*
 * Locates a Cloud Foundry deployment, otherwise exits with an error if not found
 */
private DeploymentDto getDeploymentOrExit(CloudFoundryClient client, Long id) {
  return getDeploymentOr(client, id) { msg ->
    println msg
    exit 1
  }
}

private DeploymentDto getDeploymentOr(CloudFoundryClient client, Long id,Closure callable) {
  def (deployments, error) = getDeployments(client)

  if(error) {
    return callable?.call("Error listing Cloud Foundry deployments: $error")
  }
  else {
    def dep = deployments.find { DeploymentDto dep -> dep.id == id }
    if(!dep) {
      return callable?.call("No Deployment found for ID [$id].")
    }
    return dep
  }
}

/**
 * Obtains the deployment name, otherwise if it is not specified exits with an error
 */
private getDeploymentIdOrExit() {
  getDeploymentIdOr { msg ->
    println msg
    exit 1
  }
}

private getDeploymentIdOr(Closure callable) {
  def deployment = argsMap.params ? argsMap.params[0] : null
  if(!deployment) {
    deployment = metadata['cloudfoundry.deployment']
  }

  if(!deployment) {
    return callable?.call("Please specify a Cloud Foundry deployment ID. Example: grails cloud-found-deploy 1")
  } else {
    try {
      return deployment.trim().toLong()
    } catch (e) {
      return callable?.call("Invalid deployment identifer")
    }
  }

}

/*
 * Uploads an application using the given client, name and version and returns the OperationResponseWithId instance.
 * If an error occurs this method will automatically exit the JVM
 */
private OperationResponseWithId uploadApplicationInternal(CloudFoundryClient client, String app, String ver) {
  File warFile

  if(grailsSettings.hasProperty('projectWarFile')) {
     warFile = grailsSettings.projectWarFile
  }
  else {
     warFile = calculateWarFileName(app, ver)
  }

  if(warFile.exists()) {
    def appInfo = new ApplicationInfo()
    appInfo.applicationName = "$app-$ver"
    appInfo.schemaName = cloudFoundryConf?.cloudFoundry?.db?.schemaName ?: app
    appInfo.dbUserId = dataSourceConf?.dataSource?.username ?: "root"
    appInfo.dbPassword = dataSourceConf?.dataSource?.password ?: ""
    appInfo.webappContext = cloudFoundryConf?.cloudFoundry?.webappContext ?: app
    appInfo.region = getConfiguredRegion()
    appInfo.warFile = warFile

    println()
    print "Uploading application $app to Cloud Foundry.."
    try {
      def threadExecutor = { Runnable r -> new Thread(r).start() } as Executor
      def response
      final FutureTask task = new FutureTask({
          response = client.createOrUpdateApplication(appInfo)
      } as Callable)
      threadExecutor.execute task

      def sleepCount = 0
      while(!task.isDone()) {
        sleepCount+=2000
        sleep 2000
        print "${Math.round(sleepCount/1000)}s.."
      }
      println("Done.")
      println "Uploaded application $app with id: $response.id"
      return response
    } catch (e) {
      println "Error uploading application to Cloud Foundry: ${handleError(e)}"
      exit 1
    }
  }
  else {
    println "No WAR file found, run 'grails war' first!"
    exit 1
  }
}

private String getConfiguredRegion() {
  def region = argsMap.region

  if(!region) {
      region = cloudFoundryConf?.cloudFoundry?.defaultRegion
  }
  return region ?:"US_EAST_1"
}

/*
 * Returns a list of applications and an error (null if no error). Designed to be used with Groovy multiple assignment
 */
private getApplications(CloudFoundryClient client) {
  def error = null
  def applications = []
  try {
    println()
    println "Getting uploaded applications from Cloud Foundry..."
    applications = client.getUploadedApplications()
  } catch (e) {
    error = handleError(e)
  }
  return [applications,error]
}
/*
 * Returns a list of deployments and an error (null if no error). Designed to be used with Groovy multiple assignment
 */
private getDeployments(CloudFoundryClient client) {
  def deployments = []
  def error = null
  try {
    println()
    println "Getting Deployments from Cloud Foundry..."
    deployments = client.getDeployments()
  } catch (e) {
    error = handleError(e)
  }
  return [deployments, error]
}

// This is needed to support older versions of Grails
private calculateWarFileName(String appName, String appVersion) {
  String warName
  if (buildConfig.grails.war.destFile) {
    warName = buildConfig.grails.war.destFile
  }
  else {
    warName = "${basedir}${File.separatorChar}${appName}-${appVersion}.war"
  }
  return new File(warName)
}

private handleError(Throwable e) {
  if(argsMap.debug) {
    e.printStackTrace()
    println e.message
  }
  return e.message
}