@NonCPS
def getMicroServices(json) {
	def items = new groovy.json.JsonSlurper().parseText(json).items
	def microservices = []
	for (int i = 0; i < items.size(); i++) {
		microservices << items[i]['metadata']['name']
	}
	return microservices
}

@NonCPS
def getTestStatus(json) {
	return new groovy.json.JsonSlurper().parseText(json).metadata.name
}

node {
	
	properties([
	  parameters([
	     string(name: 'PRODUCT'),
	  ])
	])	
	
	def testStatus
	
	stage ("determine the status of the test environment") {
		sh "oc get project ${params.PRODUCT}-test -o json > test.json"
		def test = readFile('test.json')
		testStatus = getTestStatus(test)
		println "the target deployment is $testStatus"
	}
	
	stage ('deploy each microservice to prod') {
		if (testStatus.equals("true")) {
			sh "oc get is -n ${params.PRODUCT}-test --selector product=${params.PRODUCT} -o json > images.output"
			def images = readFile('images.output')
			def microservices = getMicroServices(images)
			microservices.each { microservice ->
				openshiftBuild namespace: "${params.PRODUCT}-cicd", buildConfig: "promote-to-prod-${microservice}", waitTime: "300000"
				openshiftVerifyBuild namespace: "${params.PRODUCT}-cicd", buildConfig: "promote-to-prod-${microservice}", waitTime: "300000" 
		  }		
		}  else {
			error("Cannot deploy microservices to production as the test environment has not passed testing")
		}
		
	}
}
