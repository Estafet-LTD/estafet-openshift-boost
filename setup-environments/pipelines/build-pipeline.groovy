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

	currentBuild.description = "Build a container from the source, then execute unit and container integration tests before promoting the container as a release candidate for acceptance testing."

	stage("checkout") {
		git branch: "master", url: "https://github.com/${params.GITHUB}/${params.REPO}"
	}
	
	stage("read the pipeline definition") {
		pipelines = readYaml file: "openshift/pipelines/pipelines.yml"
	}

	stage("update wiremock") {
		when {
			expression { pipelines.build.wiremock }
		}
		def files = findFiles(glob: 'src/integration-test/resources/*.json')
		files.each { file -> 
			def json = readFile(file.path)
			def response = httpRequest url: "http://wiremock-docker.${project}.svc:8080/__admin/mappings/new", httpMode: "POST", validResponseCodes: "201", requestBody: json
		}
	}

	stage("unit tests") {
		when {
    	expression { pipelines.build.tests }
    }
		steps {
			withMaven(mavenSettingsConfig: 'microservices-scrum') {
	      sh "mvn clean test"
	  	} 
		}
	}
	
	stage("prepare the database") {
		when {
    	expression { 
    		return pipelines.build.db; 
    	}
    }
    steps {
			withMaven(mavenSettingsConfig: 'microservices-scrum') {
		      sh "mvn clean package -P prepare-db -Dmaven.test.skip=true -Dproject=${project}"
		  }     	
    }
	}
	
	stage("reset a-mq to purge topics") {
		when {
    	expression { 
    		return pipelines.build.mq; 
    	}
    }		
    steps {
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
		if (pipelines.build.wiremock) {
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

	stage("execute the container tests") {
		when {
    	expression { 
    		return pipelines.build.tests; 
    	}
    }
    steps {
			withMaven(mavenSettingsConfig: 'microservices-scrum') {
					try {
						sh "mvn clean verify -P integration-test"
					} finally {
						if (pipelines.build.mq) {
							sh "oc set env dc/${microservice} JBOSS_A_MQ_BROKER_URL=tcp://localhost:61616 -n ${project}"	
						}
					}
			}    	
    }		
	}

	stage("deploy snapshots") {
		when {
    	expression { 
    		return pipelines.build.promote; 
    	}
    }		
    steps {
			withMaven(mavenSettingsConfig: 'microservices-scrum') {
	 			sh "mvn clean deploy -Dmaven.test.skip=true"
			}    	
    } 
	}	
	
	stage("promote the image") {
		when {
    	expression { 
    		return pipelines.build.promote; 
    	}
    }			
    steps {	
    	openshiftTag namespace: project, srcStream: microservice, srcTag: version, destinationNamespace: "${params.PRODUCT}-cicd", destinationStream: microservice, destinationTag: version
    }
	}

}

