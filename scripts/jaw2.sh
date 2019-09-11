#!/bin/bash

python3 src/python/main_baselines.py \
--env=JawEnv-v0 \
--algo=ppo \
--verbose=20 \
--init-artisynth=false \
--artisynth-model=jaw.RlJawDemo \
--test=false \
--port=8080 \
--artisynth-args="-disc true -condyleConstraints true" \
--include-current-pos=true \
--wait-action=0.1 \
--incremental_actions=True \
--reset-step=30 \
