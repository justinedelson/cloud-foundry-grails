cd ../known-jar-util
mvn -o install -Dtest=skip
cd ../cloudfoundry-client/
mvn -o install -Dtest=skip
mvn dependency:copy-dependencies
cd ../cloud-foundry-grails-plugin/
cp ../cloudfoundry-client/target/cloudfoundry-client-1.12-SNAPSHOT.jar lib
cp ../cloudfoundry-client/target/dependency/known-jar-util-1.0-SNAPSHOT.jar lib
grails package-plugin

(cd ../example-grails-app ; grails install-plugin h:/cer/code/j2eeBook/sandbox/cloudtools-server/cloud-foundry-grails-plugin/grails-cloud-foundry-0.1.zip)

