#!/bin/bash

echo "Packaging artisynth_rl_restapi and its dependencies into .jar library"
cd src/java/artisynth_rl_restapi
mvn package
cd ../../..

echo 'export CLASSPATH='"$PWD"'/src/java/artisynth_rl_models/bin/:$CLASSPATH' >> ~/.bashrc
for jar in $(ls $PWD/src/java/artisynth_rl_restapi/target/*.jar); do echo 'export CLASSPATH='"$jar"':$CLASSPATH' >> ~/.bashrc ; done
echo "Updated JAVA CLASSPATH environment variable in ~/bashrc"

if [ -z "$ARTISYNTH_HOME" ]
then
	echo -e "\e[33mARTISYNTH_HOME variable is not set\e[0m"
else
	echo 'export PATH='"$ARTISYNTH_HOME"'/bin:$PATH' >> ~/.bashrc
	echo "Added $ARTISYNTH_HOME/bin to PATH in ~/bashrc"	
fi

source ~/.bashrc
