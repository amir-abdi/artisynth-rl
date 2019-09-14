from gym.envs.registration import register

register(
    id='SpineEnv-v0',
    entry_point='artisynth_envs.envs:SpineEnvV0',
    nondeterministic=False
)

register(
    id='Point2PointEnv-v0',
    entry_point='artisynth_envs.envs:Point2PointEnvV0',
    nondeterministic=False
)

# todo: this is duplicate to have both the env id and the artisynth_model arg. The latter should be infered auto.
register(
    id='JawEnv-v0',
    entry_point='artisynth_envs.envs:JawEnvV0',
    nondeterministic=False,
)
