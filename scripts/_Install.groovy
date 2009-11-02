//
// This script is executed by Grails after plugin was installed to project.
// This script is a Gant script so you can use all special variables provided
// by Gant (such as 'baseDir' which points on project base dir). You can
// use 'ant' to access a global instance of AntBuilder
//
// For example you can create directory under project tree:
//
//    ant.mkdir(dir:"${basedir}/grails-app/jobs")
//

config = new File("${basedir}/grails-app/conf/CloudFoundry.groovy")

ant.copy(todir:"${basedir}/lib") {
  fileset(dir:"${cloudFoundryPluginDir}/lib", includes:"mysql-connector-java-*.jar")
}

println '''
Welcome to SpringSource Cloud Foundry!
--------------------------------------

To get started you need to set your Cloud Foundry account details in grails-app/conf/CloudFoundry.groovy

If you haven't got a Cloud Foundry account go to http://www.cloudfoundry.com/ and signup for one.

Further instructions are available at http://www.cloudfoundry.com/getting_started.html

--------------------------------------
'''

if(!config.exists()) {
	config.write """\
cloudFoundry.username="myusername"	
cloudFoundry.password="mypassword"
cloudFoundry.defaultRegion="US_EAST_1"
cloudFoundry.db.schemaName="${grailsAppName}"
"""

  if(confirmInput("Cloud Foundry requires MySQL to be configured appropriately. Do you want your DataSource.groovy configured automatically (will overwrite existing DataSource.groovy)?")) {
     ant.copy(file:"${cloudFoundryPluginDir}/src/templates/DataSource.groovy", todir:"$basedir/grails-app/conf")
     ant.replace(file:"$basedir/grails-app/conf/DataSource.groovy", token:"@appName@", value:grailsAppName)
  }
  else {
    println '''\
You have chosen not to have your DataSource automatically configured. You will need to configure production
settings manually similar to the below:
-------------------------------
	production {
		dataSource {
			dbCreate = "update"
            username = "myApp-user"
            password = "myApp-pass"
            url = "jdbc:mysql://dbmaster/myApp"
		}
	}
-------------------------------
'''
  }
}

