//Java Functions
def buildMaven() {
	sh "echo java Maven build:"
	def maven_Home = tool 'maven';
	sh "${maven_Home}/bin/mvn clean install -DskipTests"
}
def buildGradle() {
	sh "echo java Gradle build:"
	def gradle_Home = tool 'gradle';
	sh "${gradle_Home}/bin/gradle clean build"
}	
def buildAnt(Map config) {
	sh "echo java Ant build:"
	def ant_Home = tool 'ant';
	sh "${ant_Home}/bin/ant -f ${config.antBuildFile} ${config.antCompileTarget}"
}
def runMavenUnitTestCases(Map config) {
	def maven_Home = tool 'maven';
	sh "${maven_Home}/bin/mvn test -B -Dmaven.test.failure.ignore=true clean verify"
	sh "${maven_Home}/bin/mvn surefire-report:report"
	sh "sudo cp ${workspace}/target/site/surefire-report.html ${workspace}/${config.reportsPath}/reportUnitTest.html"
}
def runGradleTestCases() {
	def gradle_Home = tool 'gradle';
	sh "${gradle_Home}/bin/gradle clean test --no-daemon"
}
def runAntUnitTestCases(Map config) {
	def ant_Home = tool 'ant';
	sh "${ant_Home}/bin/ant -f ${config.antBuildFile} ${config.antUnitTestTarget}"
}

//dotNet Functions
def buildDotnet(Map config) {
	sh """
		dotnet clean
		dotnet build
	"""
}
def runDotnetUnitTestCases(Map config) {
	def dotNetUnitTestSrcPath = config.dotNetUnitTestSrcPath;
	def jUnitTestResultsPath = config.jUnitTestResultsPath;
	
	try {
		sh """
			mkdir ${jUnitTestResultsPath}
			cd ${dotNetUnitTestSrcPath}
			dotnet add package JUnitTestLogger --version 1.1.0
			dotnet test --logger:junit
		"""
	}catch (error) {
		sh """
			cp ${dotNetUnitTestSrcPath}/TestResults/*.xml ${workspace}/${jUnitTestResultsPath}/
			echo error: ${error}
		"""
	}
}

// Python Functions
def buildPython() {
	sh """
		sudo apt install python3-pip -y
		pip3 install -r requirements.txt
	"""
}
def runPythonUnitTestCases(Map config) {
	def jUnitTestResultsPath = config.jUnitTestResultsPath
	
		try {
			sh "pip3 install pytest-django"
			sh "py.test --junitxml=${jUnitTestResultsPath}/junitXML.xml"
		}catch (error) {
			sh "echo error: ${error}"
		}
}
// C CPP Functions
def buildWithCmake(Map config) {
	def cmakeBuildDir = config.cmakeBuildDir
	def cmakeBuildType = config.cmakeBuildType
	def cmakeGenerator = config.cmakeGenerator
	def cmakeInstallation = config.cmakeInstallation
	
	sh "rm -rf ${cmakeBuildDir}"
		sh "rm -rf ${cmakeBuildDir}"
		cmakeBuild buildDir: "${cmakeBuildDir}", buildType: "${cmakeBuildType}", generator: "${cmakeGenerator}", installation: "${cmakeInstallation}"
		sh '''
			cd '''+ cmakeBuildDir +'''
			make
		'''    
}
def runCTest(Map config) {
	def cmakeBuildDir = config.cmakeBuildDir
	def cmakeInstallation = config.cmakeInstallation
	def ctestJunitXSLPath = config.ctestJunitXSLPath
	
	try {
		dir("${workspace}/${cmakeBuildDir}") {
			ctest installation: "${cmakeInstallation}",  arguments: '-T Test'
			sh '''
				sudo apt-get install -y xsltproc
				if [ ! -f Testing/TAG ]; then
					echo "No Testing/TAG file found, did ctest successfsully run?" 1>&2
					exit 1
				fi
				latest_test=`head -n 1 < Testing/TAG`
				chmod +x Testing/$latest_test/Test.xml
				results=Testing/$latest_test/Test.xml
				xsltproc ../'''+ ctestJunitXSLPath +''' $results > junitXML.xml
			'''
		}    
		
	} catch (e) {
		dir("${workspace}/${cmakeBuildDir}") {
			sh '''
				if [ ! -f Testing/TAG ]; then
					echo "No Testing/TAG file found, did ctest successfully run?" 1>&2
					exit 1
				fi
				latest_test=`head -n 1 < Testing/TAG`
				chmod +x Testing/$latest_test/Test.xml
				results=Testing/$latest_test/Test.xml
				xsltproc ../'''+ ctestJunitXSLPath +''' $results > junitXML.xml
			'''
		}
	}
}

// GO Functions
def buildGo() {
	def go_Home = tool 'go';
	sh "${go_Home}/bin/go build ./..."
}
def runGoUnitTestCases(Map config) {
	def jUnitTestResultsPath = config.jUnitTestResultsPath;
	
	try {
		sh """
			sudo apt install gotestsum -y
			mkdir ${jUnitTestResultsPath}
			gotestsum --no-summary failed --junitfile ${jUnitTestResultsPath}/junitXML.xml
		"""
	}catch (error) {
		sh "echo error: ${error}"
	}
}

// Javascript/Typescript Functions
def npmTest() {
	try {
		npm "test -- --reporter=junit"
		//npm "test -- --reporter=mocha-junit-reporter --reporter-options mochaFile=./${jUnitTestResultsPath}/junitXML.xml"
	}catch (error) {
		sh "echo error: ${error}"
	}
}
