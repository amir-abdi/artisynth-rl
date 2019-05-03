from gym.envs.registration import register

register(
    id='SpineEnv-v0',
    entry_point='artisynth_envs.envs:SpineEnvV0',
    nondeterministic=False
)