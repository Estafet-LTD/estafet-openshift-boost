node("maven") {

	properties([
	  parameters([
	     string(name: 'GITHUB'), string(name: 'REPO'), string(name: 'PROJECT'),
	  ])
	])

	def project = "${params.PROJECT}"

	stage("checkout") {
		git branch: "master", url: "https://github.com/${params.GITHUB}/${params.REPO}"
	}

	stage("prepare the database") {
		withMaven(mavenSettingsConfig: 'microservices-scrum') {
			sh "mvn clean package -P prepare-db -Dmaven.test.skip=true -Dproject=${project}"
		}     	
	}		

}

