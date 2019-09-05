from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import os
import pprint
import torch
import numpy as np
import random
import tensorflow as tf
from dotmap import DotMap

from MBExperiment import MBExperiment
from MPC import MPC
# from config import create_config
from artisynth_envs.make_config import create_config
# import env # We run this so that the env is registered
import artisynth_envs


from common.arguments import get_parser


def set_global_seeds(seed):
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)

    np.random.seed(seed)
    random.seed(seed)

    tf.set_random_seed(seed)


def main(args, env, ctrl_type, ctrl_args, overrides, logdir):
    set_global_seeds(0)

    ctrl_args = DotMap(**{key: val for (key, val) in ctrl_args})
    cfg = create_config(args, env, ctrl_type, ctrl_args, overrides, logdir)
    cfg.pprint()

    assert ctrl_type == 'MPC'

    cfg.exp_cfg.exp_cfg.policy = MPC(cfg.ctrl_cfg)
    exp = MBExperiment(cfg.exp_cfg)

    os.makedirs(exp.logdir)
    with open(os.path.join(exp.logdir, "config.txt"), "w") as f:
        f.write(pprint.pformat(cfg.toDict()))

    exp.run_experiment()


if __name__ == "__main__":
    args = get_parser()
    main(args, args.env_name, "MPC", args.ctrl_arg, args.override, args.logdir)
