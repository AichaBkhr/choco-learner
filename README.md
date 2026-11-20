# Choco-Learner

This repository contains the implementation of DCA, CAny and CAnDy used in the paper
"Anytime Constraint Acquisition" for the modeling of combinatorial problems.

## Build

Requires JDK 17+ and Maven 3.9.9+.

    mvn compile
    mvn dependency:build-classpath -Dmdep.outputFile=cp.txt

## Run an instance of the paper

Example, CAnDy with the most general network refined in priority, on DOM_8,
with a 5 minute budget:

    java -Xmx8g -Dcandy.priority=neg -cp "target/classes:$(cat cp.txt)" \
        fr.lisn.lahdak.acquisition.expe.AcqApp -e toy_Domain -a candy -n 8 -t 300000 -o out.json

The available toys are toy_Domain, toy_BetweenMinMax, toy_LexLessEq, toy_AllDisj,
toy_Ultrametric, toy_Minimum and toy_Maximum. The algorithms are candy, dca and conacq2.
Use -Dfirstquery=neg to give the first negative example as input.

## About the platform

The sections below describe the original Choco-Learner platform.

## HOW TO DOWNLOAD JAR FILES 

Releases are available from [this git](https://gitlab.lisn.upsaclay.fr/constraint-acquisition/choco-learner/). Released versions are also available from [Maven Central](https://central.sonatype.com/artifact/fr.lirmm.cap/constraint-acquisition-platform).

## INSTALLATION

### Importing in your project

You can import Choco-Learner in your projects in the following ways:

- Using Maven:
```xml
<dependency>
    <groupId>fr.lirmm.cap</groupId>
    <artifactId>constraint-acquisition-platform</artifactId>
    <version>...</version>
</dependency>
```
- Using Gradle:
```shell
implementation group: 'fr.lirmm.cap', name: 'constraint-acquisition-platform', version: '...'
```


### Running the Jar file

The only thing you need to run the jar file is a Java Runtime Environment.
It can be found [here](https://www.java.com/en/download/) ([See more details about jar files usages](https://docs.oracle.com/javase/tutorial/deployment/jar/basicsindex.html)).  
If you want to use the source code, the folder "dependencies" of this project contains all the necessary libraries to run the project.

### To run a test with the latest project's version generated .jar 

A credentialed software [GNUPG](https://www.gnupg.org/download/index.html#pinentry) is necessary to test out the following steps.

Run the following command :
```shell
mvn clean install -U
```
Then, to use the newly generated .jar : 
```shell
java -jar .\target\constraint-acquisition-platform-0.0.1c-SNAPSHOT.jar -h
```

## USING Choco-Learner

Choco-Learner can be used in the following ways:

### Running the Jar file

To get available options:
```shell
java -jar constraint-acquisition-platform.jar -h
```

To run a defined experience (expe = {toy1, toy2, sudoku}): 
```shell
java -jar constraint-acquisition-platform.jar -e <expe>
```

To run an experience using the default learner associated with:
```shell
java -jar constraint-acquisition-platform.jar -e toy1 -al
```

To run an experience using the a .jar learner:
```shell
java -jar constraint-acquisition-platform.jar -e toy1 -l <learner location>
```

To run a custom experiment using a .acq file:
```shell
java -jar constraint-acquisition-platform.jar -c <file location>
```

To send results by mail once the treatment is done:
```shell
java -jar constraint-acquisition-platform.jar -e toy1 -mail <yourmail@address>
```

### Importing in Java code

The ```src/fr/lisn/lahdak/acquisition/examples``` directory contains examples of how Choco-Learner's components can be used in a project.

## DEPENDENCIES

To use and compile the source code, make sure you have the following libraries:
- [javax activation](https://mvnrepository.com/artifact/javax.activation/activation/1.1)
- [choco solver 4.10.0](https://github.com/chocoteam/choco-solver/releases/tag/4.10.0)
- [javamail api](https://javaee.github.io/javamail/)


For more uses (unit tests and graphic mode), add the extra libraries :
- [javaFX](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html) (contained in the jdk8)
- [JUnit](https://mvnrepository.com/artifact/junit/junit/4.12)
