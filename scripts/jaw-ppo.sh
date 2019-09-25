#!/bin/bash

python3 src/python/main_baselines.py \
--project-name=artisynth-rl-jaw \
--model-name=ppo2 \
--env=JawEnv-v0 \
--alg=ppo2 \
--verbose=20 \
--init-artisynth=true \
--test=false \
--port=8090 \
--include-current-pos=true \
--wait-action=0.1 \
--incremental_actions=True \
--reset-step=30 \
--save-interval=1 \
--log-interval=1 \
--nsteps=2048 \
--num_timestep=204800 \
--use-wandb=false \

