import logging
import time

import numpy as np
import torch

from common import constants as c
from common.utilities import Bunch
from artisynth_envs.artisynth_base_env import ArtiSynthBase

logger = logging.getLogger(c.LOGGER_STR)


class JawEnvV0(ArtiSynthBase):
    def __init__(self, wait_action, reset_step, goal_threshold, goal_reward, **kwargs):
        self.args = Bunch(kwargs)
        super().__init__(**kwargs)

        self.episode_counter = 0
        self.action_size = 0
        self.obs_size = 0
        self.goal_threshold = float(goal_threshold)

        self.reset_step = int(reset_step)
        self.wait_action = float(wait_action)

        self.goal_reward = goal_reward

        self.action_size, self.obs_size = self.init_spaces(incremental_actions=self.incremental_actions)

    def state_dict2tensor(self, state):
        return torch.tensor(self.state_dic_to_array(state))

    def get_state_tensor(self):
        state_dict = self.get_state_dict()
        return self.state_dict2tensor(state_dict)

    def step(self, action):
        action = self.wrap_action(action)
        logger.debug('action:{}'.format(action))
        self.episode_counter += 1

        self.take_action(action)

        time.sleep(self.wait_action)
        state = self.get_state_dict()

        reward, done, info = self.calc_reward(state, action)
        state_array = self.state_dic_to_array(state)

        if self.episode_counter >= self.reset_step:
            done = True

        return state_array, reward, done, info

    def calc_reward(self, state, action):
        observation = state[c.OBSERVATION_STR]
        thres = self.goal_threshold
        info = {}

        phi_u = self.distance_to_target(observation)

        done = False
        done_reward = 0
        if phi_u < thres:
            done = True
            done_reward = self.goal_reward
            logging.info(f'Done: {phi_u} < {thres}')

        excitations = action
        phi_r = np.mean(excitations)  # phi_r = np.linalg.norm(excitations)
        reward = done_reward - phi_u * self.w_u - phi_r * self.w_r

        info['distance'] = phi_u
        info['excitations'] = phi_r
        logger.log(level=18, msg='reward={}  phi_u={}   phi_r={}'.format(reward, phi_u, phi_r))

        return reward, done, info

    def reset(self):
        self.episode_counter = 0
        return super().reset()


