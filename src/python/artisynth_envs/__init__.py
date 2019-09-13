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
    kwargs={'port': '8080', 'wait_action': '0.1', 'eval_mode': False, 'reset_step': '30',
            'ip': 'localhost', 'include_current_pos': True, 'artisynth_model': 'jaw.RlJawDemo',
            'artisynth_args': "-disc false -condyleConstraints true", 'incremental_actions': True,
            'goal_threshold': '0.03', 'goal_reward': 5},
)
