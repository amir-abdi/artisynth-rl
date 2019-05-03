#!/bin/bash

echo "Packaging artisynth_rl_restapi and its dependencies into .jar library"
cd src/java/artisynth_rl_restapi
mvn package
cd ../../..

export CLASSPATH=$PWD/src/java/artisynth_rl_models/build/:$CLASSPATH
for jar in $(ls $PWD/src/java/artisynth_rl_restapi/target/*.jar); do export CLASSPATH=$jar:$CLASSPATH; done
echo "Updated JAVA CLASSPATH environment variable"

if [ -z "$ARTISYNTH_HOME" ]
then
	echo -e "\e[33mARTISYNTH_HOME variable is not set\e[0m"
else
	export PATH=$ARTISYNTH_HOME/bin:$PATH
	echo "Added $ARTISYNTH_HOME/bin to PATH"	
fi
