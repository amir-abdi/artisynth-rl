#!/bin/bash

# check artisynth-rl/src/python/artisynth_envs/__init__.py for list of defined environments

python3 src/python/main_keras_rl.py \
--env=Point2PointEnv-v2 \
--model_name=point2point-naf-kerasRL \
--alg=naf \
--port=8080 \
--verbose=20 \
--wait_action=0.1 \
--include_current_state=True \
--include_current_excitations=True \
--goal_reward=5 \
--test=false \

