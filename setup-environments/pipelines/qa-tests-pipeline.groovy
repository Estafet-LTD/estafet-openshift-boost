@NonCPS
def getDeploymentConfigs(json) {
	def items = new groovy.json.JsonSlurper().parseText(json).items
	def dcs = []
	for (int i = 0; i < items.size(); i++) {
		dcs << items[i]['metadata']['name']
	}
	return dcs
}

node('maven') {

	properties([
	  parameters([
	     string(name: 'GITHUB'), string(name: 'PRODUCT'), string(name: 'REPO'),
	  ])
	])

	def project = "${params.PRODUCT}-test"

	stage("checkout") {
		git branch: "master", url: "https://github.com/${params.GITHUB}/${params.REPO}"
	}

	stage("initialise test flags") {
		sh "oc get dc --selector product=${params.PRODUCT} -n ${project} -o json > microservices.json"	
		def microservices = readFile('microservices.json')
		def dcs = getDeploymentConfigs(microservices)
		println dcs
		dcs.each { dc ->
				sh "oc patch dc/${dc} -p '{\"metadata\":{\"labels\":{\"testStatus\":\"untested\"}}}' -n ${project}"
		}
	}

	stage("cucumber tests") {
		withMaven(mavenSettingsConfig: 'microservices-scrum') {
			try {
				sh "mvn clean test"	
			} finally {
				cucumber buildStatus: 'UNSTABLE', fileIncludePattern: '**/*cucumber-report.json',  trendsLimit: 10
			}
		} 
	}
	
	stage("flag this environment") {
		if (currentBuild.currentResult == 'SUCCESS') {
			println "The tests passed successfully"
			sh "oc get dc --selector product=microservices-scrum -n ${project} -o json > microservices.json"	
			def microservices = readFile('microservices.json')
			def dcs = getDeploymentConfigs(microservices)
			println dcs
			dcs.each { dc ->
					sh "oc patch dc/${dc} -p '{\"metadata\":{\"labels\":{\"testStatus\":\"passed\"}}}' -n ${project}"
			}		
		}
	}
	
}

