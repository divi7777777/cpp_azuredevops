import hudson.FilePath

def readYaml(String filePath) {
	sh "echo YAML filePath: ${filePath}"
    def configData = readYaml (file: "${filePath}")
    return configData;
}

void setConfigData() {
	def configData = readYaml("${workspace}/config.yaml")
	env.projectLanguage = configData.projectDetails.language
	env.reportsPath = configData.projectDetails.reportsPath
}
def zephyrReports(Map config) {
	def zephyrResultXmlFilePath = config.zephyrResultXmlFilePath
	def zephyrServerAddress = config.zephyrServerAddress
	def zephyrProjectKey = config.zephyrProjectKey
	def zephyrReleaseKey = config.zephyrReleaseKey
	def zephyrParserTemplateKey = config.zephyrParserTemplateKey
	def zephyrCycleDuration = config.zephyrCycleDuration
	def zephyrCycleKey = config.zephyrCycleKey
	def zephyrCyclePrefix = config.zephyrCyclePrefix
	def zephyrCreatePackage = config.zephyrCreatePackagee
	
	zeeReporter serverAddress: "${zephyrServerAddress}", projectKey: "${zephyrProjectKey}", releaseKey: "${zephyrReleaseKey}", resultXmlFilePath: "${zephyrResultXmlFilePath}", createPackage: "${zephyrCreatePackage}", cycleDuration: "${zephyrCycleDuration}", cycleKey: "${zephyrCycleKey}", cyclePrefix: '', parserTemplateKey: "${zephyrParserTemplateKey}"
    //zeeReporter createPackage: false, cycleDuration: '30 days', cycleKey: '2', cyclePrefix: '', parserTemplateKey: '1', projectKey: '1', releaseKey: '1', resultXmlFilePath: '', serverAddress: 'https://unisys.zephyrdemo.com'
}

def createJiraTicketForFailedTestCases(Map config) {
	def jUnitTestResultsPath = config.jUnitTestResultsPath
	def jUnitResultsPath = "**${jUnitTestResultsPath}/*.xml"
	def jiraProjectKey = config.jiraProjectKey
	def jiraIssueType = config.jiraIssueType
	def jiraAutoRaiseIssue = config.jiraAutoRaiseIssue
	def jiraAutoResolveIssue = config.jiraAutoResolveIssue
	def jiraAutoUnlinkIssue = config.jiraAutoUnlinkIssue
	
	junit (
		testResults: "${jUnitResultsPath}",
		skipPublishingChecks: true,
		skipMarkingBuildUnstable: true,
		testDataPublishers: [
			jiraTestResultReporter(
				configs: [
					jiraStringField(fieldKey: 'summary', value: '${DEFAULT_SUMMARY}'),
					jiraStringField(fieldKey: 'description', value: '${DEFAULT_DESCRIPTION}'),
					jiraStringArrayField(fieldKey: 'labels', values: [jiraArrayEntry(value: 'Jenkins'), jiraArrayEntry(value:'Integration')])
				],
				projectKey: "${jiraProjectKey}",
				issueType: "${jiraIssueType}",
				autoRaiseIssue: "${jiraAutoRaiseIssue}",
				autoResolveIssue: "${jiraAutoResolveIssue}",
				autoUnlinkIssue: "${jiraAutoUnlinkIssue}",
			)
		]
	)
}
def checkJunitPassPercentage(Map config) {
	def jUnitTestResultsPath = config.jUnitTestResultsPath
	def unitTestPassPercentage = config.unitTestPassPercentage
	def jUnitResultsPath = "**${jUnitTestResultsPath}/*.xml"
	def unitTestResults = junit (testResults: "${jUnitResultsPath}", skipPublishingChecks: true, skipMarkingBuildUnstable: true)
	def totalUnitTestCases =  "${unitTestResults.totalCount}".toInteger()
	def passedUnitTestCases =  "${unitTestResults.passCount}".toInteger()
	def passPercentage = 100 - (((totalUnitTestCases-passedUnitTestCases)/totalUnitTestCases)*100)
	
	if (passPercentage < unitTestPassPercentage) {
		currentBuild.result = "ABORTED"
		throw new Exception("Percentage of passed test cases is less than ${unitTestPassPercentage}")
	}
	
}

def runSonarScanner(Map config) {
	def sonarProjectKey = config.sonarProjectKey
	def sonarProjectAuthToken = config.sonarProjectAuthToken
	def sonarScannerToolName = config.sonarScannerToolName
	def sonarqubeInstanceName = config.sonarqubeInstanceName
	
	withSonarQubeEnv("${sonarqubeInstanceName}"){
		withCredentials([string(credentialsId: "${sonarProjectAuthToken}", variable: 'sonarUsrToken')]) {
			def scanner_Home = tool "${sonarScannerToolName}"
			sh "${scanner_Home}/bin/sonar-scanner -Dsonar.login=${sonarUsrToken} -Dsonar.projectKey=${sonarProjectKey} -Dsonar.java.binaries=."
			sh "echo Please refer sonarscanner usage for any clarifications: https://docs.sonarqube.org/latest/analysis/scan/sonarscanner/"
		}
	}	
}

def runSonarScannerMSBuildDotNetCore(Map config) {
	def sonarProjectAuthToken = config.sonarProjectAuthToken
	def sonarScannerToolName = config.sonarScannerToolName
	def sonarqubeInstanceName = config.sonarqubeInstanceName
	
	withSonarQubeEnv("${sonarqubeInstanceName}"){
		withCredentials([string(credentialsId: "${sonarProjectAuthToken}", variable: 'sonarUsrToken')]) {
			def scanner_Home = tool "${sonarScannerToolName}"
			sh """
				dotnet ${scanner_Home}/SonarScanner.MSBuild.dll begin /k:${sonarProjectKey} /d:sonar.login=${sonarUsrToken}"
				dotnet build ${dotNetSolutionPath}'
				dotnet ${scanner_Home}/SonarScanner.MSBuild.dll end /d:sonar.login=${sonarUsrToken}"
				echo Please refer sonarscannermsbuild usage for any clarifications: https://docs.sonarqube.org/latest/analysis/scan/sonarscanner-for-msbuild/"
			"""
		}
	}	
}

def checkSonarQualityGate(Map config) {
	def qgSleepTime = config.qgSleepTime
	sleep time: qgSleepTime, unit: 'SECONDS'
	def qg = waitForQualityGate()
	if (qg.status != 'OK'){
		sh 'echo If Pipeline fails due to "PENDING" or "INPROGRESS" please increase the time based on the report upload time.'
		error "Pipeline aborted due to quality gate failure: ${qg.status}"
	}	
}

def configureArtifactory(Map config) {
	def artifactoryServerID = config.artifactoryServerID
	def artifactoryServerURL = config.artifactoryServerURL
	def artifactoryServerCred = config.artifactoryServerCred
	rtServer (
		id: "${artifactoryServerID}",
		url: "${artifactoryServerURL}",
		credentialsId: "${artifactoryServerCred}"
	)
}

def imgPushToArtifactory(Map config){
	def artifactoryRegistryTagURL = config.artifactoryRegistryTagURL
	def artifactoryServerID = config.artifactoryServerID
	def artifactoryDockerRepoName = config.artifactoryDockerRepoName
	def imageName = config.imageName
	
	dockerTag(srcImageName:"${imageName}", srcImageTagName:"latest", targetImageName: "${artifactoryRegistryTagURL}/${artifactoryDockerRepoName}/${imageName}", targetImageTagName:"latest")
	dockerTag(srcImageName:"${imageName}", srcImageTagName:"latest", targetImageName: "${artifactoryRegistryTagURL}/${artifactoryDockerRepoName}/${imageName}", targetImageTagName:"$BUILD_NUMBER")
	rtDockerPush(
		serverId: "${artifactoryServerID}",
		image: "${artifactoryRegistryTagURL}/${artifactoryDockerRepoName}/${imageName}:latest",
		targetRepo: "${artifactoryDockerRepoName}", 
		properties: 'project-name=docker1;status=stable'
	)
	rtDockerPush(
		serverId: "${artifactoryServerID}",
		image: "${artifactoryRegistryTagURL}/${artifactoryDockerRepoName}/${imageName}:$BUILD_NUMBER",
		targetRepo: "${artifactoryDockerRepoName}", 
		properties: 'project-name=docker1;status=stable'
	)
}

def imgPullFromArtifactory(Map config){
	def artifactoryRegistryTagURL = config.artifactoryRegistryTagURL
	def artifactoryServerID = config.artifactoryServerID
	def artifactoryDockerRepoName = config.artifactoryDockerRepoName
	def imageName = config.imageName
	def imageTag = config.imageTag
	
	//if(imageTag == null) imageTag = latest
	
	rtDockerPull(
		serverId: "${artifactoryServerID}",
		image: "${artifactoryRegistryTagURL}/${artifactoryDockerRepoName}/${imageName}:${imageTag}",
		sourceRepo: "${artifactoryDockerRepoName}",
		project: "",
		javaArgs: '-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005'
	)
}		

def dockerTag(Map config) {
	def srcImageName = config.srcImageName
	def srcImageTagName = config.srcImageTagName
	def targetImageName = config.targetImageName
	def targetImageTagName = config.targetImageTagName
	
	sh """
		sudo docker tag ${srcImageName}:${srcImageTagName} ${targetImageName}:${targetImageTagName}
	"""
}
def dockerBuildImage(Map config) {
	def dockerFileLocation = config.dockerFileLocation
	def imageName = config.imageName
	
	sh "sudo docker build -f ${dockerFileLocation} -t ${imageName} ."
}

def trivyScan(Map config) {
	def trivyCleanImgName = config.trivyCleanImgName
    def trivyCleanImgVersion = config.trivyCleanImgVersion
	def imageName = config.imageName
	def imageTag = config.imageTag
	def severity = config.severity
	
	sh """
		sudo docker run --rm -v /var/run/docker.sock:/var/run/docker.sock ${trivyCleanImgName}:${trivyCleanImgVersion} -q image --exit-code 1 --no-progress -s '${severity}' ${imageName}:${imageTag}
	""" 
}

void helmSetup(Map config) {
	String version = config.version
	String installPath = '/usr/bin/'
  
	// check if current version already installed
	if (fileExists("${installPath}/helm")) {
		String installedVersion = sh(label: 'Check Helm Version', returnStdout: true, script: "${installPath}/helm version").trim()
		if (installedVersion ==~ version) {
			print "Helm version ${version} already installed at ${installPath}."
		}
	}
	// otherwise download and untar specified version
	else {
		downloadFile("https://storage.googleapis.com/kubernetes-helm/helm-v${version}-linux-amd64.tar.gz", '/tmp/helm.tar.gz')
		sh(label: 'Untar Helm CLI', script: "tar -xzf /tmp/helm.tar.gz -C ${installPath} --strip-components 1 linux-amd64/helm")
		removeFile('/tmp/helm.tar.gz')
		print "Helm successfully installed at ${installPath}/helm."
	}
}

def helmPushToHarbor(Map config){
	def registryType = config.registryType
	def registryURL = config.registryURL
	def registryCreds = config.registryCreds
	def registryRepoName = config.registryRepoName
		
	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${registryCreds}", usernameVariable: 'username', passwordVariable: 'password']]) {
		sh """
			helm repo add ${registryRepoName} ${registryURL}/chartrepo/${registryRepoName} --username $username --password $password --force-update
			helm repo update
			rm -f *.tgz
			helm package .
			helm cm-push *.tgz ${registryRepoName}
		"""
	}
}

def helmPushToArtifactory(Map config){
	def artifactoryServerURL = config.artifactoryServerURL
	def artifactoryServerCred = config.artifactoryServerCred
	def artifactoryHelmRepoName = config.artifactoryHelmRepoName
	def chartName = config.chartName
		
	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${artifactoryServerCred}", usernameVariable: 'username', passwordVariable: 'password']]) {
		sh """
			rm -f *.tgz
			helm package .
			curl -u$username:$password -T *.tgz ${artifactoryServerURL}/artifactory/${artifactoryHelmRepoName}/${chartName}.tgz
		"""
	}
}

def accuricsReport(Map config){
	def accuricsConfigFilesLocation = config.accuricsConfigFilesLocation
	def terraformLocation = config.terraformLocation
	def helmLocation = config.helmLocation
	def reportsPath = config.reportsPath
	installTerrascan()
	sh """
		sudo cp ${workspace}/${accuricsConfigFilesLocation}/accurics /usr/local/bin/
		sudo chmod +x /usr/local/bin/accurics
		export PATH=$PATH:/usr/local/bin/
	"""   
	if ( (env.helmLocation != ' ') && (fileExists("${workspace}/${helmLocation}")) ) {
		dir("${workspace}/${helmLocation}") {
			sh """
				sudo rm -rf config
				cp ${workspace}/${accuricsConfigFilesLocation}/config-helm ${workspace}/${helmLocation}/config
				accurics scan helm
				sudo cp accurics_report.html ${workspace}/${reportsPath}/reportHelm.html
				sudo rm -rf config
				sudo cp ${workspace}/${accuricsConfigFilesLocation}/config-kubernetes ${workspace}/${helmLocation}/config
				accurics scan k8s
				sudo cp accurics_report.html ${workspace}/${reportsPath}/reportk8s.html
			"""
		}
	}	
	if ( (terraformLocation != ' ') && (fileExists("${workspace}/${terraformLocation}")) ) {
		dir("${workspace}/${terraformLocation}") {
			sh """
				sudo rm -rf config
				sudo cp -f ${workspace}/${accuricsConfigFilesLocation}/config-terraform ${workspace}/${terraformLocation}/config
				accurics init
				accurics plan
				sudo cp accurics_report.html ${workspace}/${reportsPath}/reportTerraform.html
			"""
		}
	}
}

def mergeCodeToBranch(Map config) {
	def gitCred = config.gitRepoCred
	def srcBranch = config.srcBranch
	def destBranch = config.destBranch
	
	withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: "${gitCred}", usernameVariable: 'username', passwordVariable: 'password']]) {	
		sh """
			git config --local user.name ${username}
			git config --local user.password ${password}
			git config --global credential.helper store
			git checkout ${srcBranch}
			git push origin ${srcBranch}:${destBranch}
		"""
	}
}

def installTerrascan() {
	sh '''
		curl -L "$(curl -s https://api.github.com/repos/accurics/terrascan/releases/latest | grep -o -E "https://.+?_Linux_x86_64.tar.gz")" > terrascan.tar.gz
		tar -xf terrascan.tar.gz terrascan && rm terrascan.tar.gz
		sudo install terrascan /usr/local/bin && rm terrascan
	'''
}

def createJiraTicket(Map config) {
	def jiraSite = config.jiraSite
	def jiraProjectKey = config.jiraProjectKey
	def jiraIssueType = config.jiraIssueType
	def jiraIssueSummary = config.jiraIssueSummary
	def jiraIssueDescription = config.jiraIssueDescription

	def createJiraIssue = [fields: [ project: [key: "${jiraProjectKey}"], issuetype: [id: "${jiraIssueType}"], summary: "${jiraIssueSummary}", description: "${jiraIssueDescription}" ]]
    response = jiraNewIssue issue: createJiraIssue, site: "${jiraSite}"
}

def sendEmailNotification(Map config) {
	def emailToList = config.emailToList
	def emailSubject = config.emailSubject
	def reportsPath = config.reportsPath
	def emailAttachLog = config.emailAttachLog
	def emailAttachmentsPattern = config.emailAttachmentsPattern
	
	emailext (
		to: "${emailToList}",
		subject: "${emailSubject}",
		body: '''${SCRIPT, template="groovy-html.template"}''',
		mimeType: 'text/html',
		attachLog: "${emailAttachLog}",
		attachmentsPattern: "${emailAttachmentsPattern}"
	)
}

@NonCPS
void downloadFile(String url, String dest) {
	def file = null;
	// establish the file download for the master
	if (env['NODE_NAME'].equals('master')) {
		file = new File(dest).newOutputStream();
	}
	// establish the file download for the build node
	else {
		file = new FilePath(Jenkins.getInstance().getComputer(env['NODE_NAME']).getChannel(), dest).newOutputStream();
	}
	// download the file and close the ostream
	file << new URL(url).openStream();
	file.close();
}

@NonCPS
void removeFile(String file) {
	// delete a file off of the master
	if (env['NODE_NAME'].equals('master')) {
		new File(file).delete();
	}
	// delete a file off of the build node
	else {
		new FilePath(Jenkins.getInstance().getComputer(env['NODE_NAME']).getChannel(), file).delete();
	}
}