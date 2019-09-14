#!/bin/bash

python3 src/python/main_keras.py \
--env=Point2PointEnv-v0 \
--model-name=point2point-naf \
--alg=naf \
--port=8080 \
--verbose=20 \
--init-artisynth=true \
--artisynth-model=point2point.RlPoint2PointDemo \
--artisynth-args="-num 8 -demoType 2d -muscleOptLen 0.1 -radius 5" \
--wait-action=0.1 \
--test=false \


# About demoType argument:
#     demoType could have the following options: 1d|2d|3d|nonSym
#     in 1d demoType --> num is fixed to 2 muscles, which makes sense in a 1-dimensional model.
#     in 3d demoType --> num is fixed to 8 muscles