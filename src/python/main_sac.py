import logging
import os
import time
from collections import deque

import numpy as np
import torch

from artisynth_envs.make_env_pytorch import make_vec_envs, make_env
import common.config
from common.arguments import get_parser
from common.config import setup_logger

from algs.sac.train import train


def main():
    args = get_parser().parse_args()
    configs = common.config.get_config(args)

    if args.test:
        args.num_processes = 1
        args.use_wandb = False

    logger = setup_logger(args.verbose, args.model_name, configs.log_directory)
    torch.set_num_threads(1)
    device = 'cuda:0' if torch.cuda.is_available() else 'cpu'

    # set seed values
    seed = args.seed
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)

    if args.use_wandb:
        import wandb
        resume_wandb = True if args.wandb_resume_id is not None else False
        wandb.init(config=args, resume=resume_wandb, id=args.wandb_resume_id, project='rl')

    import gym
    env = gym.make(args.env, **vars(args))

    # env = make_env(args.env, args.num_processes,
    #                args.gamma, configs.log_directory, device,
    #                start_port=args.port,
    #                allow_early_resets=True, num_frame_stack=None, args=args)

    # Agent
    from algs.sac.sac import SAC
    agent = SAC(env.observation_space.shape[0], env.action_space, args)
    if args.load_path:
        logger.info(f'loading model from {args.load_path}')
        agent.load_state_dict(torch.load(args.load_path))

    train(env, agent, args, configs)


if __name__ == "__main__":
    main()
