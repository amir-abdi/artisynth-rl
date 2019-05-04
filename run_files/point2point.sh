#!/bin/bash

python3 src/python/main_keras.py \
--env-name=Point2PointEnv-v0 \
--model-name=point2point-naf \
--algo=naf \
--port=8080 \
--verbose=20 \
--init-artisynth=true \
--artisynth-model=RlPoint2PointModel \
--artisynth-args="-num 10 -demoType 1d -muscleOptLen 0.1 -radius 5" \
--wait-action=0.1 \


# demoType=[2d|3d|nonSym]
# in 3d demoType --> num is fixed to 8 muscles!