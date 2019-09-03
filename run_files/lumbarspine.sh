#!/bin/bash

python3 src/python/main_pytorch_a2c_ppo_acktr.py \
--env-name=SpineEnv-v0 \
--model-name=lumbarspine_ppo \
--log-interval=1 \
--num-steps=32 \
--save-interval=4 \
--wait-action=0.1 \
--eval-interval=1 \
--num-steps-eval=5 \
--ppo-epoch=4 \
--algo=ppo \
--num-processes=1 \
--port=8080 \
--use-wandb=false \
--num-env-steps=80000 \
--num-mini-batch=16 \
--reset-step=30 \
--entropy-coef=0.0001 \
--lr=1e-8 \
--clip-param=0.2 \
--hidden-layer-size=256 \
--use-linear-lr-decay=true \
--use-linear-clip-decay=true \
--w_u=1.0 \
--w_d=0.0001 \
--w_r=0.01 \
--goal-reward=0 \
--verbose=20 \
--init-artisynth=false \
--artisynth-model=RlLumbarSpineModel \
--test=false \


#--load-path=/home/amirabdi/artisynth_rl/results/SpineEnv-v2/lumbarspine_ppo/trained/ppo-SpineEnv-v2-1,0.0001,0.01.pt \
#--load-path=/home/amirabdi/artisynth_rl/results/SpineEnv-v2/lumbarspine_ppo/trained/ppo-SpineEnv-v2-1,0.0001,0.01.pt \ trained without /h for damping

# maybe I should switch clip-param back to default value of 0.2
#--load-path=/home/amirabdi/artisynth_rl/results/SpineEnv-v2/lumbarspine_ppo/trained/ppo-SpineEnv-v2-1,0.0001,0.01.pt \ seemed to improve a bit in the end with lr<1e-7...

#--load-path=/home/amirabdi/artisynth_rl/results/SpineEnv-v2/lumbarspine_ppo/trained/ppo-SpineEnv-v2-1,0.0001,0.1.pt \ main

#--load-path=/home/amirabdi/artisynth_rl/results/SpineEnv-v2/lumbarspine_ppo/trained/ppo-SpineEnv-v2-1,0.0001,1.0.pt \ didn't work
#--load-path=/home/amirabdi/artisynth_rl/results/SpineEnv-v2/lumbarspine_ppo/trained/ppo-SpineEnv-v2-trainedNot2500.pt \
#--load-path=/home/amirabdi/artisynth_rl/results/SpineEnv-v2/lumbarspine_ppo/trained/ppo-SpineEnv-v2-trainedNot2500.pt \

#--resume-wandb \


#--load-path=/home/amirabdi/artisynth_rl/results/SpineEnv-v2/lumbarspine_ppo/trained/ppo-SpineEnv-v2-2.5,0.0005,0.05.pt \
