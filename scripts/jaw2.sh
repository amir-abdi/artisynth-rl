#!/bin/bash

python src/python/main_baselines.py \
--env=JawEnv-v0 \
--alg=sac \
--verbose=20 \
--init-artisynth=true \
--artisynth-model=jaw.RlJawDemo \
--test=false \
--port=8080 \
--artisynth-args="-disc false -condyleConstraints true" \
--include-current-pos=true \
--wait-action=0.1 \
--incremental_actions=True \
--reset-step=30 \
