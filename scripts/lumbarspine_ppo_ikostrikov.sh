#!/bin/bash

FILENAME=$(basename $0)
FILENAME="${FILENAME%.*}"
NAME=${1:-$FILENAME}

python3 src/python/main_ikostrikov.py \
--experiment_name=$NAME \
--env=SpineEnv-v0 \
--log_interval=1 \
--num_steps=32 \
--save_interval=4 \
--wait_action=0.1 \
--eval_interval=1 \
--num_steps_eval=30 \
--ppo_epoch=4 \
--alg=ppo \
--num_processes=1 \
--port=8091 \
--use_wandb=false \
--num_env_steps=80000 \
--num_mini_batch=16 \
--reset_step=100 \
--entropy_coef=0.0001 \
--lr=7e-3 \
--clip_param=0.2 \
--hidden_layer_size=256 \
--use_linear_lr_decay=true \
--use_linear_clip_decay=true \
--w_u=1.0 \
--w_d=0.0001 \
--w_r=0.01 \
--goal_reward=0 \
--verbose=20 \
--include_current_excitations=true \
--include_current_state=true \
--test=false \
