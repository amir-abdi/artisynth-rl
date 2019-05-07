#!/bin/bash

# Build Maven project
echo "Packaging artisynth_rl_restapi and its dependencies into .jar library"
cd src/java/artisynth_rl_restapi
mvn package
cd ../../..


# Add class path of artisynth_rl_models and artisynth_rl_restapi to CLASSPATH
NEW_PATH="$PWD"'/src/java/artisynth_rl_models/bin/'
echo $NEW_PATH

if [[ ":$CLASSPATH:" == *":$NEW_PATH:"* ]]; then
	echo "Path to artisynth-rl is correctly set"
else
	echo 'export CLASSPATH='"$NEW_PATH" >> ~/.bashrc
	for jar in $(ls $PWD/src/java/artisynth_rl_restapi/target/*.jar); do echo 'export CLASSPATH='"$jar"':$CLASSPATH' >> ~/.bashrc ; done
	echo "Updated JAVA CLASSPATH environment variable in ~/bashrc"
fi

# Add $ARTISYNTH_HOME/bin to PATH to run artisynth from the command line
if [ -z "$ARTISYNTH_HOME" ]
then
	echo -e "\e[33mARTISYNTH_HOME variable is not set\e[0m"
else
	echo 'export PATH='"$ARTISYNTH_HOME"'/bin:$PATH' >> ~/.bashrc
	echo "Added $ARTISYNTH_HOME/bin to PATH in ~/bashrc"	
fi

source ~/.bashrc
