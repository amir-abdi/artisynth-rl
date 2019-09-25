import os

import gym
from gym import wrappers

from a2c_ppo_acktr.envs import VecPyTorch, VecPyTorchFrameStack
from baselines import bench
from baselines.common.vec_env.dummy_vec_env import DummyVecEnv
from baselines.common.vec_env.subproc_vec_env import SubprocVecEnv
from baselines.common.vec_env.vec_normalize import VecNormalize


def _make_env(env_id, seed, rank, log_dir, allow_early_resets, **kwargs):
    def _thunk():
        env = gym.make(env_id, **kwargs)
        env.seed(seed + rank)
        if log_dir is not None:
            env = bench.Monitor(env, os.path.join(log_dir, str(rank)), allow_early_resets=allow_early_resets)

        env.seed(seed + rank if seed is not None else None)
        return env

    return _thunk


def make_vec_envs(env_name, seed, num_processes, gamma, log_dir,
                  device, allow_early_resets, num_frame_stack=None,
                  start_port=8080, rl_library='pytorch', **kwargs):
    rl_library = rl_library.lower()
    ports = range(start_port, start_port + num_processes)

    envs = []
    for i in range(num_processes):
        kwargs['args'].port = ports[i]
        envs.append(_make_env(env_name, seed, i, log_dir, allow_early_resets, **vars(kwargs['args'])))

    if len(envs) > 1:
        envs = SubprocVecEnv(envs)
    else:
        envs = DummyVecEnv(envs)

    if len(envs.observation_space.shape) == 1:
        use_tf = True if rl_library == 'tf' else False
        if gamma is None:
            envs = VecNormalize(envs, ret=False, use_tf=use_tf)
        else:
            envs = VecNormalize(envs, gamma=gamma, use_tf=use_tf)

    if rl_library == 'pytorch':
        envs = VecPyTorch(envs, device)
        if num_frame_stack is not None:
            envs = VecPyTorchFrameStack(envs, num_frame_stack, device)
        elif len(envs.observation_space.shape) == 3:
            envs = VecPyTorchFrameStack(envs, 2, device)

    elif rl_library == 'tf':
        import tensorflow as tf
        from baselines.common.tf_util import get_session
        # from baselines.common.cmd_util import make_vec_env
        config = tf.ConfigProto(allow_soft_placement=True,
                                intra_op_parallelism_threads=1,
                                inter_op_parallelism_threads=1)
        config.gpu_options.allow_growth = True
        get_session(config=config)

    return envs


def make_env(env_name, seed, num_processes, gamma, log_dir,
             device, allow_early_resets, num_frame_stack=None,
             start_port=8080, rl_library='pytorch', **kwargs):
    rl_library = rl_library.lower()
    kwargs['args'].port = start_port
    env = _make_env(env_name, seed, 0, log_dir, allow_early_resets, **vars(kwargs['args']))

    # if len(env.observation_space.shape) == 1:
    #     use_tf = True if rl_library == 'tf' else False
    #     if gamma is None:
    #         envs = VecNormalize(env, ret=False, use_tf=use_tf)
    #     else:
    #         envs = VecNormalize(env, gamma=gamma, use_tf=use_tf)

    if rl_library == 'tf':
        import tensorflow as tf
        from baselines.common.tf_util import get_session
        # from baselines.common.cmd_util import make_vec_env
        config = tf.ConfigProto(allow_soft_placement=True,
                                intra_op_parallelism_threads=1,
                                inter_op_parallelism_threads=1)
        config.gpu_options.allow_growth = True
        get_session(config=config)

    return env
