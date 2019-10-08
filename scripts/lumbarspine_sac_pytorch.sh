#!/bin/bash

python src/python/main_sac.py \
--model_name=lumbarspine-sac-pytorch \
--env=SpineEnv-v0 \
--alg=sac \
--verbose=20 \
--test=false \
--port=8080 \
--include_current_state=true \
--include_current_excitations=true \
--wait_action=0.1 \
--incremental_actions=True \
--reset_step=100 \
--goal_reward=5 \
--goal_threshold=0.1 \
--save_interval=10 \
--eval_interval=10 \
--test=false \
--w_u=5 \
--load_path=/home/amirabdi/artisynth-rl/results/SpineEnv-v0/lumbarspine-sac-pytorch/trained/last_saved2 \
--use_wandb=true \
--start_steps=0 \
--automatic_entropy_tuning=true \
