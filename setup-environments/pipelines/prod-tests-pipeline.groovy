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

node('maven') {

	properties([
	  parameters([
	     string(name: 'GITHUB'), string(name: 'PRODUCT'), string(name: 'REPO'),
	  ])
	])

	def project = "${params.PRODUCT}-prod"
	def env

	stage("determine the environment to deploy to") {
		sh "oc get routes -l product=${params.PRODUCT} -o json -n ${project} > routeList.json"
		def routeList = readFile('routeList.json')
		def routeName = getRouteName(routeList)
		sh "oc get route ${routeName} -o json -n ${project} > route.json"
		def route = readFile('route.json')
		env = getPassive(route)
		println "the target environment is $env"
	}

	stage("checkout") {
		git branch: "master", url: "https://github.com/${params.GITHUB}/${params.REPO}-qa-prod"
	}

	stage("reset test flags for ${project}") {
		sh "oc label namespace ${project} test-passed=false --overwrite=true"	
	}

	stage("execute smoke tests") {
		withMaven(mavenSettingsConfig: 'microservices-scrum') {
			sh "mvn clean test"	
		} 
	}
	
	stage("flag this environment") {
		if (currentBuild.currentResult == 'SUCCESS') {
			println "The tests passed successfully"
			sh "oc label namespace ${project} test-passed=true --overwrite=true"
		}
	}
	
}

