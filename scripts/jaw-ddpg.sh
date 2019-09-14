#!/bin/bash

python3 src/python/main_baselines.py \
--project-name=artisynth-rl-jaw \
--model-name=ddpg \
--env=JawEnv-v0 \
--alg=ddpg \
--verbose=20 \
--init-artisynth=false \
--artisynth-model=jaw.RlJawDemo \
--test=false \
--port=8081 \
--artisynth-args="-disc false -condyleConstraints true" \
--include-current-pos=true \
--wait-action=1.0 \
--incremental_actions=True \
--reset-step=60 \
--save-interval=1 \
--log-interval=1 \
--nsteps=2048 \
--num_timestep=204800 \
--use-wandb=false \

