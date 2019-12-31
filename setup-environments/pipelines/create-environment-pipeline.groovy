@NonCPS
def getProjects(json) {
	def items = new groovy.json.JsonSlurper().parseText(json).items
	def projects = []
	for (int i = 0; i < items.size(); i++) {
		projects << items[i]['metadata']['name']
	}
	return projects.sort()
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

node {
	
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
	     string(name: 'USER'),
	     string(name: 'UPDATE'),
	     string(name: 'TEST'),
	  ])
	])
	
	stage("checkout repo") {
    git branch: "master", url: "https://github.com/${params.GITHUB}/${params.REPO}"
	}	
	
	stage ("connect as admin") {
		sh "oc login --insecure-skip-tls-verify=true -u ${params.ADMIN_USER} -p ${params.ADMIN_PASSWORD} ${params.MASTER_HOST}"
	}
	
	if (params.UPDATE.equals("false")) {
		stage ("create the namespace") {
			project = getNextProjectName()
			def title = params.PROJECT_TITLE.equals("") ? project : params.PROJECT_TITLE
			sh "oc new-project $project --display-name='${title}'"
			sh "oc label namespace $project type=dq product=${params.PRODUCT}"
			sh "oc policy add-role-to-user edit system:serviceaccount:${params.PRODUCT}-cicd:jenkins -n $project"
			sh "oc policy add-role-to-user edit ${params.USER} -n $project"
		}		
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
		sh "oc export svc postgresql -n ${params.PRODUCT}-build --as-template=postgresql | oc apply -f -"
	}
	
	stage ('create each microservice') {
		def microservices = readYaml file: "setup-environments/vars/microservices-vars.yml"
		microservices[0].each { microservice ->
			openshiftBuild namespace: "${params.PRODUCT}-cicd", buildConfig: "build-${microservice}", env : [ [ name : "DQ_PROJECT", value : project ] ]
			openshiftVerifyBuild namespace: "${params.PRODUCT}-cicd", buildConfig: "${pipeline}", waitTime: "300000" 
		}		
	}
	
}
