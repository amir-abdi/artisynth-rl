from __future__ import division
from __future__ import print_function
from __future__ import absolute_import

import numpy as np
import gym
from dotmap import DotMap
import torch
from torch import nn as nn
from torch.nn import functional as F

from config.default import _create_exp_config, _create_ctrl_config, make_bool

from DotmapUtils import get_required_argument
from config.utils import swish, get_affine_params

TORCH_DEVICE = torch.device('cuda') if torch.cuda.is_available() else torch.device('cpu')


def create_config(args, env_name, ctrl_type, ctrl_args, overrides, logdir):
    cfg = DotMap()
    type_map = DotMap(
        exp_cfg=DotMap(
            sim_cfg=DotMap(
                task_hor=int,
                stochastic=make_bool,
                noise_std=float
            ),
            exp_cfg=DotMap(
                ntrain_iters=int,
                nrollouts_per_iter=int,
                ninit_rollouts=int
            ),
            log_cfg=DotMap(
                nrecord=int,
                neval=int
            )
        ),
        ctrl_cfg=DotMap(
            per=int,
            prop_cfg=DotMap(
                model_pretrained=make_bool,
                npart=int,
                ign_var=make_bool,
            ),
            opt_cfg=DotMap(
                plan_hor=int,
            ),
            log_cfg=DotMap(
                save_all_models=make_bool,
                log_traj_preds=make_bool,
                log_particles=make_bool
            )
        )
    )

    # Why use the following complicated code?
    # Is it only for loading file based on env_name? Seems that way
    # dir_path = os.path.dirname(os.path.realpath(__file__))
    # loader = importlib.machinery.SourceFileLoader(env_name, os.path.join(dir_path, "%s.py" % env_name))
    # spec = importlib.util.spec_from_loader(loader.name, loader)
    # cfg_source = importlib.util.module_from_spec(spec)
    # loader.exec_module(cfg_source)
    cfg_module = ArtisynthConfigModule(env_name, args)

    _create_exp_config(cfg.exp_cfg, cfg_module, logdir, type_map)
    _create_ctrl_config(cfg.ctrl_cfg, cfg_module, ctrl_type, ctrl_args, type_map)

    return cfg


class PtModel(nn.Module):

    def __init__(self, ensemble_size, in_features, out_features):
        super().__init__()

        self.num_nets = ensemble_size

        self.in_features = in_features
        self.out_features = out_features

        self.lin0_w, self.lin0_b = get_affine_params(ensemble_size, in_features, 200)

        self.lin1_w, self.lin1_b = get_affine_params(ensemble_size, 200, 200)

        self.lin2_w, self.lin2_b = get_affine_params(ensemble_size, 200, 200)

        self.lin3_w, self.lin3_b = get_affine_params(ensemble_size, 200, 200)

        self.lin4_w, self.lin4_b = get_affine_params(ensemble_size, 200, out_features)

        self.inputs_mu = nn.Parameter(torch.zeros(in_features), requires_grad=False)
        self.inputs_sigma = nn.Parameter(torch.zeros(in_features), requires_grad=False)

        self.max_logvar = nn.Parameter(torch.ones(1, out_features // 2, dtype=torch.float32) / 2.0)
        self.min_logvar = nn.Parameter(- torch.ones(1, out_features // 2, dtype=torch.float32) * 10.0)

    def compute_decays(self):
        lin0_decays = 0.000025 * (self.lin0_w ** 2).sum() / 2.0
        lin1_decays = 0.00005 * (self.lin1_w ** 2).sum() / 2.0
        lin2_decays = 0.000075 * (self.lin2_w ** 2).sum() / 2.0
        lin3_decays = 0.000075 * (self.lin3_w ** 2).sum() / 2.0
        lin4_decays = 0.0001 * (self.lin4_w ** 2).sum() / 2.0

        return lin0_decays + lin1_decays + lin2_decays + lin3_decays + lin4_decays

    def fit_input_stats(self, data):
        mu = np.mean(data, axis=0, keepdims=True)
        sigma = np.std(data, axis=0, keepdims=True)
        sigma[sigma < 1e-12] = 1.0

        self.inputs_mu.data = torch.from_numpy(mu).to(TORCH_DEVICE).float()
        self.inputs_sigma.data = torch.from_numpy(sigma).to(TORCH_DEVICE).float()

    def forward(self, inputs, ret_logvar=False):
        # print('inputs', inputs.shape)
        # print('lin0_w', self.lin0_w.shape)
        # print('lin0_b', self.lin0_b.shape)
        # Transform inputs
        inputs = (inputs - self.inputs_mu) / self.inputs_sigma

        inputs = inputs.matmul(self.lin0_w) + self.lin0_b
        inputs = swish(inputs)

        inputs = inputs.matmul(self.lin1_w) + self.lin1_b
        inputs = swish(inputs)

        inputs = inputs.matmul(self.lin2_w) + self.lin2_b
        inputs = swish(inputs)

        inputs = inputs.matmul(self.lin3_w) + self.lin3_b
        inputs = swish(inputs)

        inputs = inputs.matmul(self.lin4_w) + self.lin4_b

        mean = inputs[:, :, :self.out_features // 2]

        logvar = inputs[:, :, self.out_features // 2:]
        logvar = self.max_logvar - F.softplus(self.max_logvar - logvar)
        logvar = self.min_logvar + F.softplus(logvar - self.min_logvar)

        if ret_logvar:
            return mean, logvar

        return mean, torch.exp(logvar)


class ArtisynthConfigModule:
    TASK_HORIZON = 1000
    NTRAIN_ITERS = 300
    NROLLOUTS_PER_ITER = 1
    PLAN_HOR = 30
    MODEL_IN, MODEL_OUT = 58, 42
    GP_NINDUCING_POINTS = 300

    def __init__(self, env_name, args):
        # args = DotMap(args)
        self.ENV = gym.make(env_name, **vars(args))
        self.NN_TRAIN_CFG = {"epochs": 5}
        self.OPT_CFG = {
            "Random": {
                "popsize": 2500
            },
            "CEM": {
                "popsize": 500,
                "num_elites": 50,
                "max_iters": 5,
                "alpha": 0.1
            }
        }

    @staticmethod
    def obs_preproc(obs):
        # if isinstance(obs, np.ndarray):
        #     return np.concatenate([obs[:, 1:2], np.sin(obs[:, 2:3]), np.cos(obs[:, 2:3]), obs[:, 3:]], axis=1)
        # elif isinstance(obs, torch.Tensor):
        #     return torch.cat([
        #         obs[:, 1:2],
        #         obs[:, 2:3].sin(),
        #         obs[:, 2:3].cos(),
        #         obs[:, 3:]
        #     ], dim=1)
        # assert isinstance(obs, torch.Tensor)
        # print('obs', obs)
        return obs

    @staticmethod
    def obs_postproc(obs, pred):
        return obs + pred

        # assert isinstance(obs, torch.Tensor)
        # return torch.cat([
        #     pred[:, :1],
        #     obs[:, 1:] + pred[:, 1:]
        # ], dim=1)

    @staticmethod
    def targ_proc(obs, next_obs):
        return next_obs - obs

        # if isinstance(obs, np.ndarray):
        #     return np.concatenate([next_obs[:, :1], next_obs[:, 1:] - obs[:, 1:]], axis=1)
        # elif isinstance(obs, torch.Tensor):
        #     return torch.cat([
        #         next_obs[:, :1],
        #         next_obs[:, 1:] - obs[:, 1:]
        #     ], dim=1)

    @staticmethod
    def obs_cost_fn(obs):
        obs_size = 13 # * 2
        action_size= 16
        # print('make config obs', obs.shape)
        # TODO: more like a placeholder

        return torch.mean(torch.abs(obs[:, :obs_size] - obs[:, obs_size:-action_size]))


    @staticmethod
    def ac_cost_fn(acs):
        # TODO: is this minmizing the activations? constants vary between 0.01 to 0.1!
        return 0.1 * (acs ** 2).sum(dim=1)

    def nn_constructor(self, model_init_cfg):
        ensemble_size = get_required_argument(model_init_cfg, "num_nets", "Must provide ensemble size")

        load_model = model_init_cfg.get("load_model", False)

        assert load_model is False, 'Has yet to support loading model'

        model = PtModel(ensemble_size,
                        self.MODEL_IN, self.MODEL_OUT * 2).to(TORCH_DEVICE)
        # * 2 because we output both the mean and the variance

        model.optim = torch.optim.Adam(model.parameters(), lr=0.001)

        return model


CONFIG_MODULE = ArtisynthConfigModule

# TODO: some muscles are not activated at all
# TODO: all other muscles are excited to value one... which usually means the activation of last layer is wrong.