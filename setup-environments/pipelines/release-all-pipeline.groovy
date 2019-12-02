@NonCPS
def getPipelines(json) {
	def items = new groovy.json.JsonSlurper().parseText(json).items
	def microservices = []
	for (int i = 0; i < items.size(); i++) {
		microservices << items[i]['metadata']['name']
	}
	return microservices
}

node {
	
	properties([
	  parameters([
	     string(name: 'PRODUCT'),
	  ])
	])				
	
	stage ('release all microservices') {
		sh "oc get bc -n ${params.PRODUCT}-cicd --selector app=pipeline --selector type=release -o json > images.output"
		def images = readFile('images.output')
		def pipelines = getPipelines(images)
		pipelines.each { pipeline ->
			openshiftBuild namespace: "${params.PRODUCT}-cicd", buildConfig: "${pipeline}", waitTime: "300000"
			openshiftVerifyBuild namespace: "${params.PRODUCT}-cicd", buildConfig: "${pipeline}", waitTime: "300000" 
	  }	
	}
}
