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
def recentVersion(List versions) {
	versions.sort( false ) { a, b ->
		[a,b]*.tokenize('.')*.collect { it as int }.with { u, v ->
			[u,v].transpose().findResult{ x,y-> x<=>y ?: null } ?: u.size() <=> v.size()
		}
	}[-1]
}

def username() {
    withCredentials([usernamePassword(credentialsId: 'microservices-scrum', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
        return USERNAME
    }
}

def password() {
    withCredentials([usernamePassword(credentialsId: 'microservices-scrum', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
        return PASSWORD
    }
}

def getLatestVersion(product, microservice) {
	sh "oc get is ${microservice} -o json -n ${product}-cicd > image.json"
	def image = readFile('image.json')
	def versions = getVersions(image)
	if (versions.size() == 0) {
		error("There are no images for ${microservice}")
	}
	return recentVersion(versions)
}

node('maven') {

	properties([
	  parameters([
	     string(name: 'GITHUB'), string(name: 'PRODUCT'), string(name: 'REPO'), string(name: 'MICROSERVICE'),
	  ])
	])

	def project = "${params.PRODUCT}-test"
	def microservice = params.MICROSERVICE	

	String version
	def developmentVersion
	def releaseVersion
	def pipelines
	
	stage("checkout") {
		git branch: "master", url: "https://${username()}:${password()}@github.com/${params.GITHUB}/${params.REPO}"
	}

	stage("read the pipeline definition") {
		pipelines = readYaml file: "openshift/pipelines/pipelines.yml"
	}

	stage ("verify build image") {
		version = getLatestVersion(params.PRODUCT, microservice)
		println "latest version is $version"
		def pom = readFile('pom.xml')
		def matcher = new XmlSlurper().parseText(pom).version =~ /(\d+\.\d+\.)(\d+)(\-SNAPSHOT)/
		String pomVersion = "${matcher[0][1]}${matcher[0][2].toInteger()}-SNAPSHOT"
		if (!version.equals(pomVersion)) {
			error("Source version ${pomVersion} does not match image version ${version}")
		}
	}
	
	if (pipelines.release.db[0]) {
		stage("prepare the database") {
			withMaven(mavenSettingsConfig: 'microservices-scrum') {
		      sh "mvn clean package -P prepare-db -Dmaven.test.skip=true -Dproject=${project}"
		    } 
		}	
	}
	
	stage("increment version") {
		def pom = readFile('pom.xml');
		def matcher = new XmlSlurper().parseText(pom).version =~ /(\d+\.\d+\.)(\d+)(\-SNAPSHOT)/
		developmentVersion = "${matcher[0][1]}${matcher[0][2].toInteger()+1}-SNAPSHOT"
		releaseVersion = "${matcher[0][1]}${matcher[0][2]}"
	}
	
	stage("perform release") {
    sh "git config --global user.email \"jenkins@estafet.com\""
  	sh "git config --global user.name \"jenkins\""
    withMaven(mavenSettingsConfig: 'microservices-scrum') {
			sh "mvn release:clean release:prepare release:perform -DreleaseVersion=${releaseVersion} -DdevelopmentVersion=${developmentVersion} -DpushChanges=false -DlocalCheckout=true -DpreparationGoals=initialize -B"
			sh "git push origin master"
			sh "git tag ${releaseVersion}"
			sh "git push origin ${releaseVersion}"
		} 
	}	

	stage("promote the image from ${params.PRODUCT}-cicd to ${project}") {
		openshiftTag namespace: "${params.PRODUCT}-cicd", srcStream: microservice, srcTag: version, destinationNamespace: project, destinationStream: microservice, destinationTag: releaseVersion
	}	

	stage("create deployment config") {
		sh "oc process -n ${project} -f openshift/templates/${microservice}-config.yml -p NAMESPACE=${project} -p DOCKER_NAMESPACE=${project} -p DOCKER_IMAGE_LABEL=${releaseVersion} -p PRODUCT=${params.PRODUCT} | oc apply -f -"
		sh "oc set env dc/${microservice} JAEGER_AGENT_HOST=jaeger-agent.${project}.svc JAEGER_SAMPLER_MANAGER_HOST_PORT=jaeger-agent.${project}.svc:5778 JAEGER_SAMPLER_PARAM=1 JAEGER_SAMPLER_TYPE=const -n ${project}"
	}
	
	stage("execute deployment") {
		openshiftDeploy namespace: project, depCfg: microservice,  waitTime: "3000000"
		openshiftVerifyDeployment namespace: project, depCfg: microservice, replicaCount:"1", verifyReplicaCount: "true", waitTime: "300000" 
	}

	stage("promote the image to ${params.PRODUCT}-prod") {
		openshiftTag namespace: project, srcStream: microservice, srcTag: releaseVersion, destinationNamespace: "${params.PRODUCT}-prod", destinationStream: microservice, destinationTag: releaseVersion
	}	
	
	stage("flag this microservice as untested") {
		sh "oc patch dc/${microservice} -p '{\"metadata\":{\"labels\":{\"testStatus\":\"untested\"}}}' -n ${project}"		
	}	

}

