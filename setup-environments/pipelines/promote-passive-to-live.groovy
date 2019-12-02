@NonCPS
def getTargetEnvironment(json) {
	def matcher = new groovy.json.JsonSlurper().parseText(json).items[0].spec.to.name =~ /(green|blue)(basic\-ui)/
	String namespace = matcher[0][1]
	return namespace.equals("green") ? "blue" : "green" 
}

@NonCPS
def getTestStatus(json) {
	def items = new groovy.json.JsonSlurper().parseText(json).items
	for (int i = 0; i < items.size(); i++) {
		def testStatus = items[i]['metadata']['labels']['testStatus']
		if (testStatus.equals("untested") || testStatus.equals("failed")) {
			return "false"
		}
	}
	return "true"
}

@NonCPS
def getRoutes(json) {
	def items = new groovy.json.JsonSlurper().parseText(json).items
	def routes = []
	for (int i = 0; i < items.size(); i++) {
		routes << items[i]['spec']['to']['name']
	}
	return routes
}

node {
	
	properties([
	  parameters([
	     string(name: 'PRODUCT'),
	  ])
	])		
	
	def env
	def testStatus
	def routesJson
	
	stage("determine the environment to deploy to") {
		sh "oc get route -o json -n ${params.PRODUCT}-prod > route.json"
		routesJson = readFile('route.json')
		env = getTargetEnvironment(routesJson)
		println "the target environment is $env"
	}	
	
	stage ("determine the status of the target environment") {
		sh "oc get dc --selector product=${params.PRODUCT} --selector environment=$env -n ${params.PRODUCT}-prod -o json > test.json"
		def test = readFile('test.json')
		testStatus = getTestStatus(test)
		println "the target environment test status is $testStatus"
	}	
	
	stage("make the target deployment active") {
		if (testStatus.equals("true")) {
			def routes = getRoutes(routesJson)
			routes.each { route ->
				sh "oc patch route/${route} -p '{\"spec\":{\"to\":{\"name\":\"${env}${route}\"}}}' -n ${params.PRODUCT}-prod > route.out"
				def rt = readFile('route.out')
				if (rt.indexOf("basic-ui patched") < 0) {
					error("error when patching route $route")
				}				
			}
		} else {
			error("Cannot promote $env microservices live as they have not been passed tested")
		}
	}
	
}	