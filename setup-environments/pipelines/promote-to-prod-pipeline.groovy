@NonCPS
def getVersions(json) {
	def tags = new groovy.json.JsonSlurper().parseText(json).status.tags
	def versions = []
	for (int i = 0; i < tags.size(); i++) {
		versions << tags[i]['tag']
	}
	return versions
}

@NonCPS
def getPassive(json) {
	def matcher = new groovy.json.JsonSlurper().parseText(json).spec.to.name =~ /(green|blue)(.+)/
	String namespace = matcher[0][1]
	return namespace.equals("green") ? "blue" : "green" 
}

@NonCPS
def getRouteName(json) {
	return new groovy.json.JsonSlurper().parseText(json).items[0].metadata.name
}

def recentVersion( versions ) {
	def size = versions.size()
	return versions[size-1]
}

def getLatestVersion(project, microservice) {
	sh "oc get is ${microservice} -o json -n ${project} > image.json"
	def image = readFile('image.json')
	def versions = getVersions(image)
	if (versions.size() == 0) {
		error("There are no images for ${microservice}")
	}
	return recentVersion(versions)
}

@NonCPS
def getTestStatus(json) {
	return new groovy.json.JsonSlurper().parseText(json).metadata.labels."test-passed"
}

node("maven") {

	properties([
	  parameters([
	     string(name: 'GITHUB'), string(name: 'PRODUCT'), string(name: 'REPO'), string(name: 'MICROSERVICE'),
	  ])
	])
	
	def project = "${params.PRODUCT}-prod"
	def microservice = params.MICROSERVICE
	def version
	def env
	def testStatus
	def pipelines
	
	stage("determine the environment to deploy to") {
		sh "oc get routes -l product=${params.PRODUCT} -o json -n ${project} > routeList.json"
		def routeList = readFile('routeList.json')
		def routeName = getRouteName(routeList)
		sh "oc get route ${routeName} -o json -n ${project} > route.json"
		def route = readFile('route.json')
		env = getPassive(route)
		println "the target environment is $env"
	}
	
	stage ("determine the status of the target environment") {
		sh "oc get project ${params.PRODUCT}-test -o json > test.json"
		def test = readFile('test.json')
		testStatus = getTestStatus(test)
		println "the target environment test status is $testStatus"
		if (testStatus.equals("false")) error("Cannot promote $env microservices to staging as they have not passed tested")
	}		
	
	stage("determine which image is to be deployed") {
		version = getLatestVersion project, microservice
		println "latest version is $version"
	}
	
	stage("checkout release version") {
		checkout scm: [$class: 'GitSCM', 
      userRemoteConfigs: [[url: "https://github.com/${params.GITHUB}/${params.REPO}"]], 
      branches: [[name: "refs/tags/${version}"]]], changelog: false, poll: false
	}

	stage("read the pipeline definition") {
		pipelines = readYaml file: "openshift/pipelines/pipelines.yml"
	}	
	
	stage("reset test flag for ${project}") {
		sh "oc label namespace ${project} test-passed=false --overwrite=true"	
	}	
	
	if (pipelines.promote.db[0]) {
		stage("ensure the database exists") {
			withMaven(mavenSettingsConfig: 'microservices-scrum') {
		      sh "mvn clean package -P create-prod-db -Dmaven.test.skip=true -Dproject=${project}"
		    } 
		}
	}
	
	stage("create deployment config") {
		sh "oc process -n ${project} -f openshift/templates/${microservice}-config.yml -p NAMESPACE=${project} -p ENV=${env} -p DOCKER_NAMESPACE=${project} -p DOCKER_IMAGE_LABEL=${version} -p PRODUCT=${params.PRODUCT} | oc apply -f -"
		def mq = ""
		if (pipelines.promote.mq[0]) {
			mq = "JBOSS_A_MQ_BROKER_URL=tcp://broker-amq-tcp.mq-${env}.svc:61616"
		}
		sh "oc set env dc/${env}${microservice} ${mq} JAEGER_AGENT_HOST=jaeger-agent.${project}.svc JAEGER_SAMPLER_MANAGER_HOST_PORT=jaeger-agent.${project}.svc:5778 JAEGER_SAMPLER_PARAM=1 JAEGER_SAMPLER_TYPE=const -n ${project}"	
	}
	
	stage("execute deployment") {
		openshiftDeploy namespace: project, depCfg: "${env}${microservice}",  waitTime: "3000000"
		openshiftVerifyDeployment namespace: project, depCfg: "${env}${microservice}", replicaCount:"1", verifyReplicaCount: "true", waitTime: "300000" 
	}

}

