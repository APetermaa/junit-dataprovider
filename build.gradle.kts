plugins {
    id("com.github.spotbugs") version "3.0.0" apply false
    id("de.aaschmid.cpd") version "3.1"

    id("biz.aQute.bnd") version "4.3.1" apply false

    jacoco
    id("com.github.kt3k.coveralls") version "2.9.0"
}

val isBuildOnJenkins by extra(System.getenv().getOrDefault("BUILD_TAG", "").startsWith("jenkins-"))
println("buildOnJenkins = $isBuildOnJenkins for current build.")

val skipSpotBugs by extra(hasProperty("skipSpotBugs"))
println("Using skipSpotBugs = $skipSpotBugs for current build.")

// set default junit versions if not set via command line
val junit4Version by extra(findProperty("junit4Version")?.toString() ?: "4.12")
println("Using JUnit4 version $junit4Version for current build.")
val junitJupiterVersion by extra(findProperty("junitJupiterVersion")?.toString() ?: "5.5.2")
println("Using JUnit Jupiter version $junitJupiterVersion for current build.")

buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply<JacocoPlugin>()
    apply<JavaLibraryPlugin>()
    apply<com.github.spotbugs.SpotBugsPlugin>()
    apply<aQute.bnd.gradle.BndBuilderPlugin>()

    group = "com.tngtech.junit.dataprovider"
    version = "2.7-SNAPSHOT"

    dependencies {
        "compileOnly"("com.github.spotbugs:spotbugs-annotations:3.1.5")
        "testImplementation"("com.github.spotbugs:spotbugs-annotations:3.1.5")
    }

    configure<JavaPluginExtension> {
        withJavadocJar()
        withSourcesJar()
    }

    tasks {
        withType<JavaCompile> {
            options.encoding = "UTF-8"
            options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
        }

        withType<Jar> {
            from(project.rootDir) {
                include("LICENSE.md", "LICENSE-notice.md")
                into("META-INF")
            }
        }

        named<Javadoc>("javadoc") {
            if (JavaVersion.current().isJava9Compatible) {
                (options as StandardJavadocDocletOptions).addBooleanOption("html5", true)
            }
        }
    }
}

project(":core") {
    configure<BasePluginConvention> {
        archivesBaseName = "junit-dataprovider-core"
        description = "The common core for a TestNG like dataprovider runner for JUnit."
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_6
        targetCompatibility = JavaVersion.VERSION_1_6
    }

    dependencies {
        "testImplementation"("junit:junit:4.12")

        "testImplementation"("org.assertj:assertj-core:1.7.1")
        "testImplementation"("org.mockito:mockito-core:2.18.3")
    }

    tasks {
        withType<JavaCompile> {
            options.compilerArgs.addAll(listOf("-Xlint:-options"))
        }

        named<Jar>("jar") {
            manifest {
                attributes(
                        "Automatic-Module-Name" to "com.tngtech.junit.dataprovider.core"
                )
            }
        }
    }
}

project(":junit4") {
    apply<GroovyPlugin>()

    configure<BasePluginConvention> {
        archivesBaseName = "junit4-dataprovider"
        description = "A TestNG like dataprovider runner for JUnit having a simplified syntax compared to all the existing JUnit4 features."
    }

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_6
        targetCompatibility = JavaVersion.VERSION_1_6
    }

    configure<SourceSetContainer> {
        register("integTest") {
            compileClasspath += named("main").get().output + named("test").get().output
            runtimeClasspath += named("main").get().output + named("test").get().output
        }
    }

    configurations {
        "integTestImplementation" {
            extendsFrom(configurations["testImplementation"])
        }
    }

    dependencies {
        "api"(project(":core"))
        "api"("junit:junit:${junit4Version}")

        "testImplementation"("org.assertj:assertj-core:1.7.1")
        "testImplementation"("org.mockito:mockito-core:2.18.3")

        "integTestImplementation"("org.codehaus.groovy:groovy:2.4.7")
    }

    tasks {
        withType<JavaCompile> {
            options.compilerArgs.addAll(listOf("-Xlint:-options"))
        }

        named<Jar>("jar") {
            manifest {
                attributes(
                        "Automatic-Module-Name" to "com.tngtech.junit.dataprovider.junit4"
                )
            }
        }

        val integTest = register<Test>("integTest") {
            group = "verification"
            description = "Runs all integration tests."

            ignoreFailures = isBuildOnJenkins

            classpath = project.the<SourceSetContainer>()["integTest"].runtimeClasspath
            testClassesDirs = project.the<SourceSetContainer>()["integTest"].output.classesDirs

            dependsOn(named("integTestClasses"))
        }
        val touchIntegTestResultsForJenkins = register<TouchTestResults>("touchIntegTestResultsForJenkins") {
            tasks(integTest)
            enabled = isBuildOnJenkins
        }
        named("build") {
            dependsOn(touchIntegTestResultsForJenkins)
        }
    }
}

configure(subprojects.filter { p -> p.name.startsWith("junit-jupiter") }) {
    apply<GroovyPlugin>()

    configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    configure<SourceSetContainer> {
        register("integTest") {
            compileClasspath += named("main").get().output + named("test").get().output
            runtimeClasspath += named("main").get().output + named("test").get().output
        }
    }

    configurations {
        "integTestImplementation" {
            extendsFrom(configurations["testImplementation"])
        }
    }

    dependencies {
        "api"(project(":core"))
        "api"("org.junit.jupiter:junit-jupiter-engine:${junitJupiterVersion}")

        "testImplementation"("org.assertj:assertj-core:3.8.0")
        "testImplementation"("org.mockito:mockito-core:2.18.3")

        "integTestImplementation"("org.codehaus.groovy:groovy:2.4.12")
    }

    tasks {
        withType<JavaCompile> {
            options.compilerArgs.addAll(listOf("-parameters"))
        }

        named<Test>("test") {
            useJUnitPlatform()
        }

        val integTest = register<Test>("integTest") {
            group = "verification"
            description = "Runs all integration tests."

            ignoreFailures = isBuildOnJenkins

            classpath = project.the<SourceSetContainer>()["integTest"].runtimeClasspath
            testClassesDirs = project.the<SourceSetContainer>()["integTest"].output.classesDirs

            useJUnitPlatform()
            dependsOn(named("integTestClasses"))
        }
        val touchIntegTestResultsForJenkins = register<TouchTestResults>("touchIntegTestResultsForJenkins") {
            tasks(integTest)
            enabled = isBuildOnJenkins
        }
        named("build") {
            dependsOn(touchIntegTestResultsForJenkins)
        }
    }
}

project(":junit-jupiter") {
    configure<BasePluginConvention> {
        archivesBaseName = "junit-jupiter-dataprovider"
        description = "A TestNG like dataprovider runner for JUnit Jupiter which is feature comparable to JUnit4 dataprovider."
    }

    tasks.named<Jar>("jar") {
        manifest {
            attributes(
                    "Automatic-Module-Name" to "com.tngtech.junit.dataprovider.jupiter"
            )
        }
    }
}

project(":junit-jupiter-params") {
    configure<BasePluginConvention> {
        archivesBaseName = "junit-jupiter-params-dataprovider"
        description = "A TestNG like dataprovider runner for JUnit Jupiter Parameterized Tests which is largely compatible to JUnit4 dataprovider."
    }

    dependencies {
        "api"("org.junit.jupiter:junit-jupiter-params:${junitJupiterVersion}")
    }

    tasks.named<Jar>("jar") {
        manifest {
            attributes(
                    "Automatic-Module-Name" to "com.tngtech.junit.dataprovider.jupiter.params"
            )
        }
    }
}

// configure after properties are set and integration tests are added
subprojects {
    configure<JacocoPluginExtension> {
        toolVersion = "0.8.3"
    }

    configure<com.github.spotbugs.SpotBugsExtension> {
        toolVersion = "3.1.12"
        isIgnoreFailures = true
    }

    tasks {
        named<Jar>("jar") {
            manifest {
                val now = java.time.LocalDate.now()

                val title = project.the<BasePluginConvention>().archivesBaseName
                val company = "TNG Technology Consulting GmbH"
                val today = now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val copyright = "${now.year} $company"

                attributes(
                        "Built-By" to "Gradle ${gradle.gradleVersion}",
                        "Built-Date" to today, // using now would destroy incremental build feature
                        "Specification-Title" to title,
                        "Specification-Version" to archiveVersion,
                        "Specification-Vendor" to company,
                        "Implementation-Title" to title,
                        "Implementation-Version" to archiveVersion,
                        "Implementation-Vendor" to company,
                        "Issue-Tracker" to "https://github.com/TNG/junit-dataprovider/issues",
                        "Documentation-URL" to "https://github.com/TNG/junit-dataprovider/wiki",
                        "Copyright" to copyright,
                        "License" to "Apache License v2.0, January 2004",

                        // OSGi / p2 plugin information
                        "Bundle-Copyright" to copyright,
                        "Bundle-Name" to title,
                        "Bundle-SymbolicName" to "${project.group}.$title",
                        "Bundle-Vendor" to company,
                        "Export-Package" to "com.tngtech.junit.dataprovider.*"
                )
            }
        }

        val test = named<Test>("test") {
            ignoreFailures = isBuildOnJenkins
        }

        val touchTestResultsForJenkins = register<TouchTestResults>("touchTestResultsForJenkins") {
            tasks(test)
            enabled = isBuildOnJenkins
        }
        named("build") {
            dependsOn(touchTestResultsForJenkins)
        }

        named<JacocoReport>("jacocoTestReport") {
            reports {
                xml.isEnabled = true // coveralls plugin depends on xml format report
            }
        }

        withType<com.github.spotbugs.SpotBugsTask> {
            enabled = !skipSpotBugs
        }

        plugins.withType<LifecycleBasePlugin> {
            named("check") {
                dependsOn(rootProject.tasks["cpdCheck"])
            }
        }
    }
}

configure<de.aaschmid.gradle.plugins.cpd.CpdExtension> {
    // Use Java 8 and later to execute cpd successfully
    toolVersion = "6.13.0"
}

tasks.named<de.aaschmid.gradle.plugins.cpd.Cpd>("cpdCheck") {
    ignoreFailures = true
    minimumTokenCount = 25
    setSource(files(
            // only check java source code
            subprojects.flatMap { p -> p.the<SourceSetContainer>()["main"].java.srcDirs },
            subprojects.flatMap { p -> p.the<SourceSetContainer>()["test"].java.srcDirs }
    ))
}

// -- coveralls plugin multi-module project workaround ---------------------------------------------------------
val publishedProjects = subprojects.filter { true }

val jacocoMerge = tasks.register("jacocoMerge", JacocoMerge::class) {
    doFirst {
        executionData = files(executionData.filter { it.exists() })
    }
    publishedProjects.forEach { p ->
        executionData(p.tasks.withType(Test::class))
    }
}

val jacocoRootReport = tasks.register("jacocoRootReport", JacocoReport::class) {
    description = "Generates an aggregate report from all subprojects"

    additionalSourceDirs.from(publishedProjects.flatMap { p -> p.the<SourceSetContainer>()["main"].allSource.srcDirs })
    sourceDirectories.from(publishedProjects.flatMap { p -> p.the<SourceSetContainer>()["main"].allSource.srcDirs })
    classDirectories.from(publishedProjects.flatMap { p -> p.the<SourceSetContainer>()["main"].output })

    executionData(jacocoMerge.get().destinationFile)

    reports {
        xml.isEnabled = true // required by coveralls
    }
    dependsOn(publishedProjects.map { p -> p.tasks.named("test") }, jacocoMerge)
}

coveralls {
    sourceDirs = publishedProjects.flatMap { p -> p.the<SourceSetContainer>()["main"].allSource.srcDirs }.map { f -> f.absolutePath }
    jacocoReportPath = "${buildDir}/reports/jacoco/jacocoRootReport/jacocoRootReport.xml"
}

// -- sign and publish artifacts -------------------------------------------------------------------------------------
val isReleaseVersion by extra(!project.version.toString().endsWith("-SNAPSHOT"))

// username and password from gradle.properties otherwise empty
val sonatypeUsername by extra(findProperty("sonatypeUsername")?.toString() ?: "")
val sonatypePassword by extra(findProperty("sonatypePassword")?.toString() ?: "")

subprojects {
    apply<MavenPublishPlugin>()
    apply<SigningPlugin>()

    tasks.withType<GenerateModuleMetadata> {
        enabled = isReleaseVersion // signing of these artifacts causes failure for snapshot versions
    }

    configure<PublishingExtension> {
        publications {
            register<MavenPublication>("mavenJava") {
                val archivesBaseName = project.the<BasePluginConvention>().archivesBaseName
                artifactId = archivesBaseName
                from(components["java"])
                pom {
                    packaging = "jar"

                    name.set(archivesBaseName)
                    description.set(project.description)
                    url.set("https://github.com/TNG/junit-dataprovider")

                    developers {
                        developer {
                            id.set("aaschmid")
                            name.set("Andreas Schmid")
                            email.set("service@aaschmid.de")
                        }
                    }

                    licenses {
                        license {
                            name.set("The Apache Software License, Version 2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }

                    scm {
                        connection.set("scm:git@github.com:TNG/junit-dataprovider.git")
                        developerConnection.set("scm:git@github.com:TNG/junit-dataprovider.git")
                        url.set("scm:git@github.com:TNG/junit-dataprovider.git")
                    }

                    withXml {
                        fun org.w3c.dom.NodeList.asElementList() = (0 until length).map { this::item }.filterIsInstance<org.w3c.dom.Element>()

                        asElement()
                                .getElementsByTagName("dependencies")
                                .asElementList()
                                .flatMap { deps -> deps.childNodes.asElementList() }
                                .forEach { dep ->
                                    val groupId = dep.getElementsByTagName("groupId").item(0)
                                    val artifactId = dep.getElementsByTagName("artifactId").item(0)

                                    // JUnit4
                                    if (groupId.textContent == "junit" && artifactId.textContent == "junit") {
                                        dep.getElementsByTagName("version").item(0).textContent = "[4.10,4.12]"
                                        dep.getElementsByTagName("scope").item(0).textContent = "provided"
                                    }

                                    // JUnit5
                                    if ((groupId.textContent == "org.junit.jupiter" && artifactId.textContent == "junit-jupiter-engine") ||
                                            (groupId.textContent == "org.junit.jupiter" && artifactId.textContent == "junit-jupiter-params")) {
                                        dep.getElementsByTagName("version").item(0).textContent = "[5.5.0-M6,6.0.0)"
                                        dep.getElementsByTagName("scope").item(0).textContent = "provided"
                                    }
                                }
                    }
                }
                // finally call `asNode` to get rid of excessive newlines caused by use of asElement
                // see also https://github.com/gradle/gradle/issues/7529
                afterEvaluate {
                    pom {
                        withXml {
                            asNode()
                        }
                    }
                }
            }
        }

        repositories {
            maven {
                val releasesRepoUrl = uri("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
                val snapshotRepoUrl = uri("https://oss.sonatype.org/content/repositories/snapshots/")
                url = if (isReleaseVersion) releasesRepoUrl else snapshotRepoUrl

                credentials {
                    username = sonatypeUsername
                    password = sonatypePassword
                }

                metadataSources {
                    gradleMetadata()
                }
            }
        }
    }

    // requires gradle.properties, see http://www.gradle.org/docs/current/userguide/signing_plugin.html
    configure<SigningExtension> {
        setRequired({ isReleaseVersion && gradle.taskGraph.hasTask("publish") })
        sign(the<PublishingExtension>().publications["mavenJava"])
    }
}

// -- Custom tasks ------------------------------------------------------------
/**
 * Task to touch all junit xml report files for all given {@link Test} {@code tasks}.
 * This is required due to Jenkins fails if test output is created before build starts which
 * could happen using Gradles up-to-date feature :(
 */
open class TouchTestResults : DefaultTask() {
    @InputFiles
    val tasks = mutableListOf<TaskProvider<Test>>()

    fun tasks(vararg testTasks: TaskProvider<Test>) {
        tasks.addAll(testTasks)
        mustRunAfter(testTasks)
    }

    @TaskAction
    fun touch() {
        tasks.forEach { test ->
            val testResultsDir = test.get().reports.junitXml.destination
            if (testResultsDir.exists()) {
                val timestamp = System.currentTimeMillis()
                testResultsDir.listFiles()?.forEach { file ->
                    file.setLastModified(timestamp)
                }
            }
        }
    }
}
