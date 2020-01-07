@NonCPS
def getProjects(json) {
	def items = new groovy.json.JsonSlurper().parseText(json).items
	def projects = []
	for (int i = 0; i < items.size(); i++) {
		projects << items[i]['metadata']['name']
	}
	return projects.sort()
}

def getNextProjectName(product) {
	sh "oc get projects -l type=${product}-dq -o json > projects.json"
	def json = readFile('projects.json')	
	def projects = getProjects(json)
	if (projects.isEmpty()) {
		return "${product}-dq00"
	} else {
		def matcher = projects.last() =~ /(.*)(dq)(\d+\d+)/
		def env = "${matcher[0][3].toInteger()+1}"
		return "${product}-${matcher[0][2]}${env.padLeft(2, '0')}"	
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
	  ])
	])
	
	stage("checkout repo") {
    git branch: "master", url: "https://github.com/${params.GITHUB}/${params.REPO}"
	}	
	
	stage ("connect as admin") {
		sh "oc login --insecure-skip-tls-verify=true -u ${params.ADMIN_USER} -p ${params.ADMIN_PASSWORD} ${params.MASTER_HOST}"
	}
	
	stage ("create the namespace") {
		project = getNextProjectName(params.PRODUCT)
		def title = params.PROJECT_TITLE.equals("") ? project : params.PROJECT_TITLE
		sh "oc new-project $project --display-name='${title}'"
		sh "oc label namespace $project type=${params.PRODUCT}-dq product=${params.PRODUCT}"
		sh "oc policy add-role-to-user edit system:serviceaccount:${params.PRODUCT}-cicd:jenkins -n $project"
		sh "oc policy add-role-to-user edit ${params.USER} -n $project"
	}		
	
	stage ("create the database endpoint") {
		sh "oc export svc postgresql -n ${project} --as-template=postgresql"
		sh " oc process postgresql | oc apply -f -"
	}
	
}
