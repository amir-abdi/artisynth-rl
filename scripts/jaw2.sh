#!/bin/bash

python src/python/main_baselines.py \
--project-name=artisynth-rl-jaw \
--model-name=ppo \
--env=JawEnv-v0 \
--alg=sac \
--verbose=20 \
--init-artisynth=True \
--artisynth-model=jaw.RlJawDemo \
--test=false \
--port=8080 \
--artisynth-args="-disc false -condyleConstraints true" \
--include-current-pos=true \
--wait-action=0.1 \
--incremental_actions=True \
--reset-step=30 \
--save-interval=1 \
--log-interval=1 \
--nsteps=2048 \
--num_timestep=204800 \
--use-wandb=true \
