#!/bin/bash

python src/python/main_baselines.py \
--project-name=artisynth-rl-jaw \
--model-name=sac \
--env=JawEnv-v0 \
--alg=sac \
--verbose=20 \
--init-artisynth=True \
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
--use-wandb=true \
--goal_reward=5 \
--goal_threshold=0.03 \
