import os

import gym
from gym import wrappers

from baselines import bench
from baselines.common.vec_env.dummy_vec_env import DummyVecEnv
from baselines.common.vec_env.subproc_vec_env import SubprocVecEnv
from baselines.common.vec_env.vec_normalize import VecNormalize


def _make_env_fn(env_id, rank, seed, log_dir, allow_early_resets, **kwargs):
    def _thunk():
        env = gym.make(env_id, **kwargs)
        env.seed(seed + rank)
        if log_dir is not None:
            env = bench.Monitor(env, os.path.join(log_dir, str(rank)), allow_early_resets=allow_early_resets)

        env.seed(seed + rank if seed is not None else None)
        return env

    return _thunk


def make_vec_envs(id, num_processes, gamma, return_evn_vector=False, **kwargs):
    start_port = kwargs['port']
    ports = range(start_port, start_port + num_processes)

    env_vector = []
    for i in range(num_processes):
        kwargs['port'] = ports[i]
        env_vector.append(_make_env_fn(id, i, **kwargs))

    if len(env_vector) > 1:
        envs = SubprocVecEnv(env_vector)
    else:
        envs = DummyVecEnv(env_vector)

    if len(envs.observation_space.shape) == 1:
        use_tf = True
        if gamma is None:
            envs = VecNormalize(envs, ret=False, use_tf=use_tf)
        else:
            envs = VecNormalize(envs, gamma=gamma, use_tf=use_tf)

    import tensorflow as tf
    from baselines.common.tf_util import get_session
    config = tf.ConfigProto(allow_soft_placement=True,
                            intra_op_parallelism_threads=1,
                            inter_op_parallelism_threads=1)
    config.gpu_options.allow_growth = True
    get_session(config=config)

    if return_evn_vector:
        return envs, env_vector
    return envs


def make_vec_envs_pytorch(id, num_processes, gamma, device, return_evn_vector=False, **kwargs):
    from a2c_ppo_acktr.envs import VecPyTorch
    start_port = kwargs['port']
    ports = range(start_port, start_port + num_processes)

    env_vector = []
    for i in range(num_processes):
        kwargs['port'] = ports[i]
        env_vector.append(_make_env_fn(id, i, **kwargs))

    if len(env_vector) > 1:
        envs = SubprocVecEnv(env_vector)
    else:
        envs = DummyVecEnv(env_vector)

    if len(envs.observation_space.shape) == 1:
        if gamma is None:
            envs = VecNormalize(envs, ret=False)
        else:
            envs = VecNormalize(envs, gamma=gamma)

    envs = VecPyTorch(envs, device)

    if return_evn_vector:
        return envs, env_vector
    return envs


def wrap_env_pytorch(env, gamma, device):
    from a2c_ppo_acktr.envs import VecPyTorch
    envs = DummyVecEnv([env])

    if len(envs.observation_space.shape) == 1:
        if gamma is None:
            envs = VecNormalize(envs, ret=False)
        else:
            envs = VecNormalize(envs, gamma=gamma)
    envs = VecPyTorch(envs, device)
    return envs


def make_env(env_name, num_processes, gamma, log_dir,
             device, allow_early_resets, num_frame_stack=None,
             start_port=8080, rl_library='pytorch', **kwargs):
    rl_library = rl_library.lower()
    kwargs['args'].port = start_port
    env = _make_env_fn(env_id=env_name, rank=0, log_dir=log_dir,
                       allow_early_resets=allow_early_resets, **vars(kwargs['args']))
    env = DummyVecEnv(env)

    return env
