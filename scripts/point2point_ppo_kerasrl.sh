#!/bin/bash

FILENAME=$(basename $0)
FILENAME="${FILENAME%.*}"
NAME=${1:-$FILENAME}

echo "check artisynth-rl/src/python/artisynth_envs/__init__.py for list of defined environments"

python3 src/python/main_keras_rl.py \
--experiment_name=$NAME \
--env=Point2PointEnv-v2 \
--alg=naf \
--port=8080 \
--verbose=20 \
--wait_action=0.1 \
--include_current_state=True \
--include_current_excitations=True \
--goal_reward=5 \
--test=false \

