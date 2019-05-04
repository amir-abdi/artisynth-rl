#!/bin/bash

python3 src/python/main_keras.py \
--env-name=Point2PointEnv-v0 \
--model-name=point2point-naf \
--algo=naf \
--num-processes=1 \
--port=8080 \
--verbose=20 \
--init-artisynth=true \
--artisynth-model=RlPoint2PointModel \




#--test


#--load-path=/home/amirabdi/artisynth_rl/results/SpineEnv-v2/lumbarspine_ppo/trained/ppo-SpineEnv-v2-1,0.0001,0.01.pt \
#--load-path=/home/amirabdi/artisynth_rl/results/SpineEnv-v2/lumbarspine_ppo/trained/ppo-SpineEnv-v2-1,0.0001,0.01.pt \ trained without /h for damping

# maybe I should switch clip-param back to default value of 0.2
#--load-path=/home/amirabdi/artisynth_rl/results/SpineEnv-v2/lumbarspine_ppo/trained/ppo-SpineEnv-v2-1,0.0001,0.01.pt \ seemed to improve a bit in the end with lr<1e-7...

#--load-path=/home/amirabdi/artisynth_rl/results/SpineEnv-v2/lumbarspine_ppo/trained/ppo-SpineEnv-v2-1,0.0001,0.1.pt \ main

#--load-path=/home/amirabdi/artisynth_rl/results/SpineEnv-v2/lumbarspine_ppo/trained/ppo-SpineEnv-v2-1,0.0001,1.0.pt \ didn't work
#--load-path=/home/amirabdi/artisynth_rl/results/SpineEnv-v2/lumbarspine_ppo/trained/ppo-SpineEnv-v2-trainedNot2500.pt \
#--load-path=/home/amirabdi/artisynth_rl/results/SpineEnv-v2/lumbarspine_ppo/trained/ppo-SpineEnv-v2-trainedNot2500.pt \

#--resume-wandb \


#--load-path=/home/amirabdi/artisynth_rl/results/SpineEnv-v2/lumbarspine_ppo/trained/ppo-SpineEnv-v2-2.5,0.0005,0.05.pt \
