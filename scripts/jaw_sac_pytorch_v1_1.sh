#!/bin/bash

python src/python/main_sac.py \
--model_name=jaw-sac-pytorch-v1-1 \
--env=JawEnv-v0 \
--alg=sac \
--verbose=20 \
--test=false \
--port=8081 \
--include_current_state=true \
--include_current_excitations=true \
--wait_action=0.1 \
--incremental_actions=true \
--reset_step=100 \
--use_wandb=true \
--goal_reward=20 \
--goal_threshold=0.5 \
--save_interval=10 \
--eval_interval=10 \
--test=false \
--automatic_entropy_tuning=false \
--w_r=5 \
--w_u=1 \
--w_d=0 \

