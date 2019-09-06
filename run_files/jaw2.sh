#!/bin/bash

python3 src/python/baseline_run.py \
--env=JawEnv-v0 \
--algo=ppo \
--verbose=20 \
--init-artisynth=true \
--artisynth-model=jaw.RlJawDemo \
--test=false \

