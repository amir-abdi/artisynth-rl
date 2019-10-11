#!/bin/bash

python src/python/main_sac.py \
--model_name=jaw-sac-pytorch-v1-0 \
--env=JawEnv-v0 \
--alg=sac \
--verbose=20 \
--test=false \
--port=8080 \
--include_current_state=true \
--include_current_excitations=true \
--wait_action=0.1 \
--incremental_actions=true \
--reset_step=100 \
--use_wandb=true \
--goal_reward=5 \
--goal_threshold=0.5 \
--save_interval=10 \
--eval_interval=10 \
--test=false \
--automatic_entropy_tuning=false \
--w_r=0 \
--w_u=1 \
--w_d=0 \

#--load_path=/home/amirabdi/artisynth-rl/results/JawEnv-v0/jaw-sac-pytorch-test/trained/last \

