#!/bin/bash

python src/python/main_sac.py \
--model_name=jaw-sac-pytorch \
--env=JawEnv-v0 \
--alg=sac \
--verbose=20 \
--test=false \
--port=8080 \
--include_current_state=true \
--wait_action=0.1 \
--incremental_actions=True \
--reset_step=100 \
--use_wandb=false \
--goal_reward=5 \
--goal_threshold=0.5 \
--save_interval=10 \
--eval_interval=10 \
--test=false \
