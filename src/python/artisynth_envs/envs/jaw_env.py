import logging
import time

import numpy as np
import torch

from common import constants as c
from common.utilities import Bunch
from artisynth_envs.artisynth_base_env import ArtiSynthBase

logger = logging.getLogger(c.LOGGER_STR)


class JawEnvV0(ArtiSynthBase):
    def __init__(self, wait_action, reset_step, include_current_state, goal_threshold,
                 incremental_actions, goal_reward, include_current_excitations, w_u, w_d, w_r, **kwargs):
        self.args = Bunch(kwargs)
        super().__init__(**kwargs)

        self.episode_counter = 0
        self.action_size = 0
        self.obs_size = 0
        self.goal_threshold = float(goal_threshold)

        self.reset_step = int(reset_step)
        self.wait_action = float(wait_action)

        self.w_u = w_u
        self.w_d = w_d  # not used!
        self.w_r = w_r

        self.include_excitations = include_current_excitations
        self.include_current_state = include_current_state
        self.goal_reward = goal_reward
        self.incremental_actions = incremental_actions

        self.action_size, self.obs_size = self.init_spaces(incremental_actions=self.incremental_actions)

    def state_dict2tensor(self, state):
        return torch.tensor(self.state_dic_to_array(state))

    def get_state_tensor(self):
        state_dict = self.get_state_dict()
        return self.state_dict2tensor(state_dict)

    def step(self, action):
        logger.debug('action:{}'.format(action))
        self.episode_counter += 1

        current_excitations = np.array(self.get_excitations_dict())

        if self.incremental_actions:
            # todo: get excitations from previous state not by calling the environment again!
            self.take_action(action + current_excitations)
        else:
            self.take_action(action)

        time.sleep(self.wait_action)
        state = self.get_state_dict()

        if state is not None:
            if self.incremental_actions:
                reward, done, info = self.calc_reward(state, action + current_excitations)
            else:
                reward, done, info = self.calc_reward(state, action)
            state_array = self.state_dic_to_array(state)
        else:
            reward = 0
            done = False
            state_array = np.zeros(self.obs_size)
            info = {}

        if self.episode_counter >= self.reset_step and not self.test_mode:
            done = True

        return state_array, reward, done, info

    def calc_reward(self, state, action):
        observation = state[c.OBSERVATION_STR]
        thres = self.goal_threshold
        info = {}

        phi_u = self.distance_to_target(observation)

        info['distance'] = phi_u
        done = False
        done_reward = 0
        if phi_u < thres:
            done = True
            done_reward = self.goal_reward
            logging.info(f'Done: {phi_u} < {thres}')

        excitations = action
        phi_r = np.inner(excitations, excitations)

        reward = done_reward - phi_u * self.w_u - phi_r * self.w_r

        logger.log(level=18, msg='reward={}  phi_u={}   phi_r={}'.format(reward, phi_u, phi_r))
        return reward, done, info

    def reset(self):
        self.episode_counter = 0
        return super().reset()


