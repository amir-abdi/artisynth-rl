#!/bin/bash

FILENAME=$(basename $0)
FILENAME="${FILENAME%.*}"
NAME=${1:-$FILENAME}

python src/python/main_sac.py \
--experiment_name=$NAME \
--env=JawEnv-v2 \
--alg=sac \
--verbose=20 \
--test=false \
--port=1115 \
--include_current_state=true \
--include_current_excitations=true \
--wait_action=0.003 \
--incremental_actions=True \
--reset_step=20 \
--use_wandb=true \
--goal_reward=0 \
--goal_threshold=0.1 \
--save_interval=40 \
--eval_interval=40 \
--test=false \
--automatic_entropy_tuning=false \
--w_r=0.5 \
--w_u=100 \
--w_d=0 \
--test=false \
--gui=false \
--zero_excitations_on_reset=false \
--alpha=0.3 \
--lr_gamma=0.999995 \
--lr=0.0005 \
--lr_min=0.0003 \

