#!/bin/bash

python src/python/main_sac.py \
--model_name=jaw-sac-pytorch-v1-3-5 \
--env=JawEnv-v1 \
--alg=sac \
--verbose=20 \
--test=false \
--port=8085 \
--include_current_state=true \
--include_current_excitations=true \
--wait_action=0.1 \
--incremental_actions=True \
--reset_step=200 \
--use_wandb=true \
--goal_reward=50 \
--goal_threshold=1.0 \
--save_interval=10 \
--eval_interval=10 \
--test=false \
--automatic_entropy_tuning=false \
--w_r=70 \
--w_u=1 \
--w_d=0 \
--test=false \
--load_path=/home/amirabdi/artisynth-rl/results/JawEnv-v1/jaw-sac-pytorch-v1-3/trained/saved

