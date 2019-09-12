#!/bin/bash

python3 src/python/main_baselines.py \
--project-name=artisynth-rl-jaw \
--model-name=ppo \
--env=JawEnv-v0 \
--algo=ppo \
--verbose=20 \
--init-artisynth=false \
--artisynth-model=jaw.RlJawDemo \
--test=false \
--port=8080 \
--artisynth-args="-disc true -condyleConstraints true" \
--include-current-pos=true \
--wait-action=0.1 \
--incremental_actions=True \
--reset-step=30 \
--save-interval=1 \
--log-interval=1 \
--nsteps=2048 \
--num_timestep=20480 \
--load-path=/home/amirabdi/artisynth-rl/results/JawEnv-v0/ppo/checkpoints/00008 \

#--use_wandb=false \

