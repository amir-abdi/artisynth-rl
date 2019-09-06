#!/bin/bash

python3 src/python/main_baselines.py \
--env=JawEnv-v0 \
--algo=ppo \
--verbose=20 \
--init-artisynth=true \
--artisynth-model=jaw.RlJawDemo \
--test=false \

