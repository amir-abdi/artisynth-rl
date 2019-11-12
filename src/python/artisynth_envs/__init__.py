from common import constants as c
from gym.envs.registration import register

register(
    id='Point2PointEnv-v1',
    entry_point='artisynth_envs.envs:Point2PointEnv',
    nondeterministic=False,
    kwargs={'artisynth_model': 'artisynth.models.rl.point2point.RlPoint2PointDemo',
            'artisynth_args': '-demoType 1d -muscleOptLen 0.1 -radius 8',
            c.COMPONENTS: {
                c.CURRENT: [{c.NAME: 'point'}],
                c.TARGET: [{c.NAME: 'point_ref'}],
                c.PROPS: ['position']}
            }
)

register(
    id='Point2PointEnv-v2',
    entry_point='artisynth_envs.envs:Point2PointEnv',
    nondeterministic=False,
    kwargs={'artisynth_model': 'artisynth.models.rl.point2point.RlPoint2PointDemo',
            'artisynth_args': '-num 8 -demoType 2d -muscleOptLen 0.1 -radius 5',
            c.COMPONENTS: {
                c.CURRENT: [{c.NAME: 'point'}],
                c.TARGET: [{c.NAME: 'point_ref'}],
                c.PROPS: ['position']}
            }
)

register(
    id='Point2PointEnv-v3',
    entry_point='artisynth_envs.envs:Point2PointEnv',
    nondeterministic=False,
    kwargs={'artisynth_model': 'artisynth.models.rl.point2point.RlPoint2PointDemo',
            'artisynth_args': '-demoType 3d -muscleOptLen 0.1 -radius 5',
            c.COMPONENTS: {
                c.CURRENT: [{c.NAME: 'point'}],
                c.TARGET: [{c.NAME: 'point_ref'}],
                c.PROPS: ['position']}
            }
)

register(
    id='Point2PointEnv-v4',
    entry_point='artisynth_envs.envs:Point2PointEnv',
    nondeterministic=False,
    kwargs={'artisynth_model': 'artisynth.models.rl.point2point.RlPoint2PointDemo',
            'artisynth_args': '-demoType nonSym -muscleOptLen 0.1 -radius 5',
            c.COMPONENTS: {
                c.CURRENT: [{c.NAME: 'point'}],
                c.TARGET: [{c.NAME: 'point_ref'}],
                c.PROPS: ['position']}
            }
)

register(
    id='SpineEnv-v0',
    entry_point='artisynth_envs.envs:SpineEnvV0',
    nondeterministic=False,
    kwargs={'artisynth_model': 'artisynth.models.rl.lumbarspine.RlLumbarSpineDemo',
            c.COMPONENTS: {
                c.CURRENT: [{c.NAME: 'thorax',
                             c.LOW: [-1.0, -1.0, -1.0, -1.0],
                             c.HIGH: [1.0, 1.0, 1.0, 1.0]},
                            {c.NAME: 'L1',
                             c.LOW: [-1.0, -1.0, -1.0, -1.0],
                             c.HIGH: [1.0, 1.0, 1.0, 1.0]},
                            {c.NAME: 'L2',
                             c.LOW: [-1.0, -1.0, -1.0, -1.0],
                             c.HIGH: [1.0, 1.0, 1.0, 1.0]},
                            {c.NAME: 'L3',
                             c.LOW: [-1.0, -1.0, -1.0, -1.0],
                             c.HIGH: [1.0, 1.0, 1.0, 1.0]},
                            {c.NAME: 'L4',
                             c.LOW: [-1.0, -1.0, -1.0, -1.0],
                             c.HIGH: [1.0, 1.0, 1.0, 1.0]},
                            {c.NAME: 'L5',
                             c.LOW: [-1.0, -1.0, -1.0, -1.0],
                             c.HIGH: [1.0, 1.0, 1.0, 1.0]}],
                c.TARGET: [{c.NAME: 'thorax_ref',
                            c.LOW: [-1.0, -1.0, -1.0, -1.0],
                            c.HIGH: [1.0, 1.0, 1.0, 1.0]},
                           {c.NAME: 'L1_ref',
                            c.LOW: [-1.0, -1.0, -1.0, -1.0],
                            c.HIGH: [1.0, 1.0, 1.0, 1.0]},
                           {c.NAME: 'L2_ref',
                            c.LOW: [-1.0, -1.0, -1.0, -1.0],
                            c.HIGH: [1.0, 1.0, 1.0, 1.0]},
                           {c.NAME: 'L3_ref',
                            c.LOW: [-1.0, -1.0, -1.0, -1.0],
                            c.HIGH: [1.0, 1.0, 1.0, 1.0]},
                           {c.NAME: 'L4_ref',
                            c.LOW: [-1.0, -1.0, -1.0, -1.0],
                            c.HIGH: [1.0, 1.0, 1.0, 1.0]},
                           {c.NAME: 'L5_ref',
                            c.LOW: [-1.0, -1.0, -1.0, -1.0],
                            c.HIGH: [1.0, 1.0, 1.0, 1.0]}],
                c.PROPS: ['orientation']}
            }
)

register(
    id='JawEnv-v0',
    entry_point='artisynth_envs.envs:JawEnvV0',
    nondeterministic=False,
    kwargs={'artisynth_model': 'artisynth.models.rl.jaw.RlJawDemo',
            'artisynth_args': '-disc false -condyleConstraints true -condylarCapsule false',
            c.COMPONENTS: {
                c.CURRENT: [
                    {c.NAME: 'lowerincisor',
                     c.LOW: [-7, -105, -80],
                     c.HIGH: [14, -80, -35]
                     }],
                c.TARGET: [
                    {c.NAME: 'lowerincisor_ref',
                     c.LOW: [-7, -105, -80],
                     c.HIGH: [14, -80, -35]
                     }],
                c.PROPS: ['position']},
            }
)

register(
    id='JawEnv-v1',
    entry_point='artisynth_envs.envs:JawEnvV0',
    nondeterministic=False,
    kwargs={'artisynth_model': 'artisynth.models.rl.jaw.RlJawDemo',
            'artisynth_args': '-disc false -condyleConstraints false -condylarCapsule true',
            c.COMPONENTS: {
                c.CURRENT: [
                    {c.NAME: 'lowerincisor',
                     c.LOW: [-7, -105, -80],
                     c.HIGH: [14, -80, -35]
                     }],
                c.TARGET: [
                    {c.NAME: 'lowerincisor_ref',
                     c.LOW: [-7, -105, -80],
                     c.HIGH: [14, -80, -35]
                     }],
                c.PROPS: ['position']},
            }
)

register(
    id='JawEnv-v2',
    entry_point='artisynth_envs.envs:JawEnvV0',
    nondeterministic=False,
    kwargs={'artisynth_model': 'artisynth.models.rl.jaw.RlJawDemo',
            'artisynth_args': '-disc false -condyleConstraints false -condylarCapsule true',
            c.COMPONENTS: {
                c.CURRENT: [
                    {c.NAME: 'lowerincisor',
                     c.LOW: [-7, -105, -80, -10, -10, -10],
                     c.HIGH: [14, -80, -35, 10, 10, 10]
                     }],
                c.TARGET: [
                    {c.NAME: 'lowerincisor_ref',
                     c.LOW: [-7, -105, -80, -10, -10, -10],
                     c.HIGH: [14, -80, -35, 10, 10, 10]
                     }],
                c.PROPS: ['position', 'velocity']},
            }
)
