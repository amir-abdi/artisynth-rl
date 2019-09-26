#!/bin/bash

python src/python/main_sac.py \
--project-name=artisynth-rl-jaw \
--model-name=sac \
--env=JawEnv-v0 \
--alg=sac \
--verbose=20 \
--init-artisynth=False \
--test=false \
--port=8080 \
--include-current-pos=true \
--wait-action=0.1 \
--incremental_actions=True \
--reset-step=100 \
--use-wandb=true \
--goal-reward=5 \
--goal-threshold=0.5 \
--save-interval=10 \
--eval-interval=10 \

#--load-path=/home/amirabdi/artisynth-rl/results/JawEnv-v0/sac/trained/saved_1 \
#--test=true \
