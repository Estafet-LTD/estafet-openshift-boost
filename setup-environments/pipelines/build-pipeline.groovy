@NonCPS
def getVersion(pom) {
	def matcher = new XmlSlurper().parseText(pom).version =~ /(\d+\.\d+\.)(\d+)(\-SNAPSHOT)/
	return "${matcher[0][1]}${matcher[0][2].toInteger()}-SNAPSHOT"
}

node("maven") {

	properties([
	  parameters([
	     string(name: 'GITHUB'), string(name: 'PRODUCT'), string(name: 'REPO'), string(name: 'MICROSERVICE'),
	  ])
	])

	def project = "${params.PRODUCT}-build"
	def microservice = params.MICROSERVICE	
	def version
	def pipelines
	boolean db
	boolean mq
	boolean wiremock
	boolean promote
	boolean tests

	currentBuild.description = "Build a container from the source, then execute unit and container integration tests before promoting the container as a release candidate for acceptance testing."

	stage("checkout") {
		git branch: "master", url: "https://github.com/${params.GITHUB}/${params.REPO}"
	}
	
	stage("read the pipeline definition") {
		pipelines = readYaml file: "openshift/pipelines/pipelines.yml"
		db = pipelines.build.db
		mq = pipelines.build.mq
		wiremock = pipelines.build.wiremock
		promote = pipelines.build.promote
		tests = pipelines.build.tests
		println "db - ${db}"
		println "mq - ${mq}"
		println "wiremock - ${wiremock}"
		println "promote - ${promote}"
		println "tests - ${tests}"
	}

	if (wiremock) {
		stage("update wiremock") {
			def files = findFiles(glob: 'src/integration-test/resources/*.json')
			files.each { file -> 
				def json = readFile(file.path)
				def response = httpRequest url: "http://wiremock-docker.${project}.svc:8080/__admin/mappings/new", httpMode: "POST", validResponseCodes: "201", requestBody: json
			}
		}		
	}

	if (tests) {
		stage("unit tests") {
			withMaven(mavenSettingsConfig: 'microservices-scrum') {
		     sh "mvn clean test"
		  } 
		}		
	}
	
	if (db) {
		stage("prepare the database") {
			withMaven(mavenSettingsConfig: 'microservices-scrum') {
				sh "mvn clean package -P prepare-db -Dmaven.test.skip=true -Dproject=${project}"
			}     	
		}		
	}
	
	if (mq) {
		stage("reset a-mq to purge topics") {
			openshiftDeploy namespace: project, depCfg: "broker-amq"
			openshiftVerifyDeployment namespace: project, depCfg: "broker-amq", replicaCount:"1", verifyReplicaCount: "true", waitTime: "300000"    	
		}		
	}
	
	stage("reset the promoted image stream") {
		def pom = readFile('pom.xml')
		version = getVersion(pom)
		sh "oc tag -d ${microservice}:${version} -n ${params.PRODUCT}-cicd || true"
	}	
	
	stage("create build config") {
			sh "oc process -n ${project} -f openshift/templates/${microservice}-build-config.yml -p NAMESPACE=${project} -p GITHUB=${params.GITHUB} -p DOCKER_IMAGE_LABEL=${version} -p PRODUCT=${params.PRODUCT} | oc apply -f -"
	}

	stage("execute build") {
		openshiftBuild namespace: project, buildConfig: microservice
		openshiftVerifyBuild namespace: project, buildConfig: microservice, waitTime: "300000" 
	}

	stage("create deployment config") {
		sh "oc process -n ${project} -f openshift/templates/${microservice}-config.yml -p NAMESPACE=${project} -p DOCKER_NAMESPACE=${project} -p DOCKER_IMAGE_LABEL=${version} -p PRODUCT=${params.PRODUCT} | oc apply -f -"
		if (wiremock) {
			def envVars = ""
			pipelines.build.wiremock_environment_variables[0].each {
				def name = it['name']
				def value = it['value']
				envVars = "${envVars} ${name}=${value}"
			}
			sh "oc set env dc/${microservice} ${envVars} -n ${project}"	
		}
	}

	stage("execute deployment") {
		openshiftDeploy namespace: project, depCfg: microservice
		openshiftVerifyDeployment namespace: project, depCfg: microservice, replicaCount:"1", verifyReplicaCount: "true", waitTime: "300000" 
	}

	if (tests) {
		stage("execute the container tests") {
			withMaven(mavenSettingsConfig: 'microservices-scrum') {
				try {
					sh "mvn clean verify -P integration-test"
				} finally {
					if (mq) {
						sh "oc set env dc/${microservice} JBOSS_A_MQ_BROKER_URL=tcp://localhost:61616 -n ${project}"	
					}
				}
			}    		
		}		
	}

	if (promote) {
		stage("deploy snapshots") {
			withMaven(mavenSettingsConfig: 'microservices-scrum') {
		 		sh "mvn clean deploy -Dmaven.test.skip=true"
			}    	
		}			
	}
	
	if (promote) {
		stage("promote the image") {
			openshiftTag namespace: project, srcStream: microservice, srcTag: version, destinationNamespace: "${params.PRODUCT}-cicd", destinationStream: microservice, destinationTag: version
		}		
	}

}

