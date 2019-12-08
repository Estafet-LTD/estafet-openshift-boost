@NonCPS
def getProjects(json) {
	def items = new groovy.json.JsonSlurper().parseText(json).items
	def projects = []
	for (int i = 0; i < items.size(); i++) {
		projects << items[i]['metadata']['name']
	}
	return projects.sort()
}

@NonCPS
def getDataBaseExternalName(json) {
	return new groovy.json.JsonSlurper().parseText(json).spec.externalName
}

def getNextProjectName() {
	sh "oc get projects --selector type=dq -o json > projects.json"
	def json = readFile('projects.json')	
	def projects = getProjects(json)
	if (projects.isEmpty()) {
		return "dq00"
	} else {
		def matcher = projects.last() =~ /(dq)(\d+\d+)/
		def env = "${matcher[0][2].toInteger()+1}"
		return "${matcher[0][1]}${env.padLeft(2, '0')}"	
	}
}

def getDatabaseEndPoint() {
	sh "oc get service postgresql -o json -n ${params.PRODUCT}-build > db.json"
	def db = readFile('db.json')	
	return getDataBaseExternalName(db)
}

node("maven") {
	
	def project
	
	properties([
	  parameters([
	     string(name: 'GITHUB'), 
	     string(name: 'REPO'), 
	     string(name: 'PROJECT_TITLE'), 
	     string(name: 'MASTER_HOST'), 
	     string(name: 'ADMIN_USER'), 
	     string(name: 'ADMIN_PASSWORD'),
	     string(name: 'PRODUCT'),
	  ])
	])
	
	stage("checkout project") {
		checkout([$class: 'GitSCM', 
							branches: [[name: '*/master']], 
							disableSubmodules: false,
              parentCredentials: true,
        			recursiveSubmodules: true,
        			extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: ${params.REPO}]], 
        			submoduleCfg: [], 
        			userRemoteConfigs: [[url: "https://github.com/${params.GITHUB}/${params.REPO}.git"]]])
	}	
	
	stage ("connect as admin") {
		sh "oc login --insecure-skip-tls-verify=true -u ${params.ADMIN_USER} -p ${params.ADMIN_PASSWORD} ${params.MASTER_HOST}"
	}
	
	stage ("create the namespace") {
		project = getNextProjectName()
		sh "oc new-project $project --display-name='${params.PROJECT_TITLE}'"
		sh "oc label namespace $project type=dq"
		sh "oc policy add-role-to-user edit system:serviceaccount:${params.PRODUCT}-cicd:jenkins -n $project"
	}
	
	stage ("create the message broker") {
		sh "oc process amq63-basic -n openshift -p IMAGE_STREAM_NAMESPACE=openshift -p MQ_USERNAME=amq -p MQ_PASSWORD=amq | oc create -f -"
		sh "oc set probe dc/broker-amq --readiness --remove"
		openshiftVerifyDeployment namespace: project, depCfg: "broker-amq", replicaCount:"1", verifyReplicaCount: "true", waitTime: "300000" 
	}
	
	stage ("create the jaeger server") {
		sh "oc process -f https://raw.githubusercontent.com/jaegertracing/jaeger-openshift/master/all-in-one/jaeger-all-in-one-template.yml | oc create -f -"
	}
	
	stage ("create the database endpoint") {
		def database = getDatabaseEndPoint()
		sh "oc process -f estafet-microservices-scrum/setup-environments/templates/database-service.yml -p DB_HOST=$database | oc apply -f -"
	}
	
	stage ('create each microservice') {
		def microservices = readYaml file: "${params.REPO}/setup-environments/vars/microservices-vars.yml"
		def serviceEnvs = "-e JAEGER_SAMPLER_TYPE=const -e JAEGER_SAMPLER_PARAM=1 -e JAEGER_SAMPLER_MANAGER_HOST_PORT=jaeger-agent.${project}.svc:5778 -e JAEGER_AGENT_HOST=jaeger-agent.${project}.svc"
		microservices.each { microservice ->
			if ( microservice.db) {
				withMaven(mavenSettingsConfig: 'microservices-scrum') {
	      	sh "mvn clean package -P prepare-db -Dmaven.test.skip=true -Dproject=${project}"
	    	} 
				sh "oc new-app redhat-openjdk18-openshift:1.4~https://github.com/${params.GITHUB}/${params.REPO} --name=${microservice.name} -e ${microservice.db_url_env}=jdbc:postgresql://postgresql.${project}.svc:5432/{{ project }}-{{ microservice }} -e {{ db_user_env }}=postgres -e {{ db_db_env }}=welcome1 -e JBOSS_A_MQ_BROKER_URL={{ amq_url }} -e JBOSS_A_MQ_BROKER_USER={{ broker_pod_user }} -e JBOSS_A_MQ_BROKER_PASSWORD={{ broker_pod_password }} ${serviceEnvs}"				
			} else {
				sh "oc new-app redhat-openjdk18-openshift:1.4~https://github.com/${params.GITHUB}/${params.REPO} --name=${microservice.name} ${serviceEnvs}"
			}
			sh "oc set probe dc/${microservice.name} --readiness --initial-delay-seconds=30 --timeout-seconds=1 --get-url=http://:8080/api"
			openshiftVerifyDeployment namespace: project, depCfg: microservice.name, replicaCount:"1", verifyReplicaCount: "true", waitTime: "300000" 
		}		
	}
	
}
