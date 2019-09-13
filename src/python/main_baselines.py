import sys
import multiprocessing
import os.path as osp
import tensorflow as tf
import numpy as np

from baselines.common.vec_env import VecEnv
from baselines.common.cmd_util import common_arg_parser, parse_unknown_args, make_vec_env
from baselines.common.tf_util import get_session
import baselines.logger
# from baselines import logger
from importlib import import_module

import common.arguments
import common.config
import common.utilities
import artisynth_envs
import gym
import wandb

try:
    from mpi4py import MPI
except ImportError:
    MPI = None


def main(args):
    # configure logger, disable logging in child MPI processes (with rank > 0)
    arg_parser = common_arg_parser()
    arg_parser = common.arguments.get_parser(arg_parser)
    args, unknown_args = arg_parser.parse_known_args(args)
    extra_args = parse_cmdline_kwargs(unknown_args)
    configs = common.config.get_config(args)

    if MPI is None or MPI.COMM_WORLD.Get_rank() == 0:
        rank = 0
    else:
        rank = MPI.COMM_WORLD.Get_rank()

    logger = common.config.setup_logger(args.verbose, args.model_name, configs.log_directory)
    logger_formats = ['stdout', 'log', 'csv']
    if args.use_wandb:
        logger_formats.append('wandb')

    # baselines.logger.configure(configs.model_path, logger_formats, **vars(args))

    model, env = train(args, extra_args)

    if args.save_path is not None and rank == 0:
        save_path = osp.expanduser(args.save_path)
        model.save(save_path)

    if args.play:
        logger.log("Running trained model")
        obs = env.reset()

        state = model.initial_state if hasattr(model, 'initial_state') else None
        dones = np.zeros((1,))

        episode_rew = 0
        while True:
            if state is not None:
                actions, _, state, _ = model.step(obs, S=state, M=dones)
            else:
                actions, _, _, _ = model.step(obs)

            obs, rew, done, _ = env.step(actions)
            episode_rew += rew[0] if isinstance(env, VecEnv) else rew
            env.render()
            done = done.any() if isinstance(done, np.ndarray) else done
            if done:
                print('episode_rew={}'.format(episode_rew))
                episode_rew = 0
                obs = env.reset()

    env.close()

    return model


def train(args, extra_args):
    env_type, env_id = common.utilities.get_env_type(args)
    print('env_type: {}'.format(env_type))

    total_timesteps = int(args.num_timesteps)
    seed = args.seed

    learn = get_learn_function(args.alg)
    alg_kwargs = get_learn_function_defaults(args.alg, env_type)
    alg_kwargs.update(extra_args)

    env = build_env(args)

    if args.network:
        alg_kwargs['network'] = args.network
    else:
        if alg_kwargs.get('network') is None:
            alg_kwargs['network'] = get_default_network()

    print('Training {} on {}:{} with arguments \n{}'.format(args.alg, env_type, env_id, alg_kwargs))

    if args.alg == 'sac':
        model = learn(
            env=env)
    else:
        model = learn(
            env=env,
            seed=seed,
            total_timesteps=total_timesteps,
            **alg_kwargs
        )

    return model, env


def build_env(args):
    ncpu = multiprocessing.cpu_count()
    if sys.platform == 'darwin': ncpu //= 2
    nenv = args.num_env or ncpu
    alg = args.alg
    seed = args.seed

    env_type, env_id = common.utilities.get_env_type(args)

    config = tf.ConfigProto(allow_soft_placement=True,
                            intra_op_parallelism_threads=1,
                            inter_op_parallelism_threads=1)
    config.gpu_options.allow_growth = True
    get_session(config=config)

    flatten_dict_observations = alg not in {'her'}
    if alg == 'sac':
        env_args = {args.port, args.include_current_pos, args.wait_action, args.reset_step}
        print('Environment args are:', args.port, args.include_current_pos, args.wait_action, args.reset_step)
        env = gym.make(env_id,
                       )
    else:
        env = make_vec_env(env_id, env_type, args.num_env or 1, seed,
                           env_kwargs=vars(args),
                           reward_scale=args.reward_scale,
                           flatten_dict_observations=flatten_dict_observations)

    return env


def get_default_network():
    return 'mlp'


def get_alg_module(alg, submodule=None):
    submodule = submodule or alg
    try:
        # first try to import the alg module from baselines
        alg_module = import_module('.'.join(['baselines', alg, submodule]))
    except ImportError:
        # then from rl_algs
        alg_module = import_module('.'.join(['rl_' + 'algs', alg, submodule]))

    return alg_module


def get_learn_function(alg):
    return get_alg_module(alg).learn


def get_learn_function_defaults(alg, env_type):
    try:
        alg_defaults = get_alg_module(alg, 'defaults')
        kwargs = getattr(alg_defaults, env_type)()
    except (ImportError, AttributeError):
        kwargs = {}
    return kwargs


def parse_cmdline_kwargs(args):
    """
    convert a list of '='-spaced command-line arguments to a dictionary, evaluating python objects when possible
    """

    def parse(v):

        assert isinstance(v, str)
        try:
            return eval(v)
        except (NameError, SyntaxError):
            return v

    return {k: parse(v) for k, v in parse_unknown_args(args).items()}


if __name__ == '__main__':
    main(sys.argv)
