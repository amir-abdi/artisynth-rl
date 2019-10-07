import logging
import time

import numpy as np
import torch

from common import constants as c
from common.utilities import Bunch
from artisynth_envs.artisynth_base_env import ArtiSynthBase

logger = logging.getLogger(c.LOGGER_STR)


class SpineEnvV0(ArtiSynthBase):
    def __init__(self, wait_action, reset_step, include_current_state, goal_reward, goal_threshold,
                 w_u, w_d, w_r, **kwargs):

        self.args = Bunch(kwargs)
        super().__init__(**kwargs)

        self.prev_exc = None
        self.episode_counter = 0
        self.reset_step = reset_step
        self.wait_action = wait_action
        self.include_current_state = include_current_state
        self.goal_reward = goal_reward
        self.goal_threshold = goal_threshold

        self.w_u = w_u
        self.w_d = w_d
        self.w_r = w_r

        self.init_spaces()

    def state_dict2tensor(self, state):
        return torch.tensor(self.state_dic_to_array(state))

    def get_state_tensor(self):
        state_dict = self.get_state_dict()
        return self.state_dict2tensor(state_dict)

    phi_r_episode = []

    def calc_reward(self, state, excitations):
        '''
        This reward is the exact copy of the FDAT solver
        :param state:
        :return:
        '''
        observation = state['observation']
        thres = self.goal_threshold
        info = {'distance': 0,
                'vel': 0}

        h = 0.01
        w_u = self.w_u
        w_d = self.w_d
        w_r = self.w_r
        w_u *= w_u

        phi_u = self.distance_to_target(observation)

        phi_u_orig = phi_u
        phi_u /= (2 * h)

        info['distance'] = phi_u_orig
        done = False
        done_reward = 0

        if phi_u_orig < thres:
            done = True
            done_reward = self.goal_reward

        phi_d = 0
        if self.prev_exc is not None:
            diff_exc = excitations - self.prev_exc
            phi_d = np.inner(diff_exc, diff_exc) / (2 * h)

        self.prev_exc = excitations

        phi_r = np.inner(excitations, excitations) / 2
        self.phi_r_episode.append(phi_r)

        reward = -(phi_u * w_u + phi_d * w_d + phi_r * w_r) / 10
        reward += done_reward

        logger.log(level=19, msg='reward=-({} + {} + {})/10'.format(reward, phi_u * w_u, phi_d * w_d, phi_r * w_r))
        return reward, done, info

    def step(self, action):
        self.episode_counter += 1

        logger.debug('action:{}'.format(action))
        self.take_action(action)

        time.sleep(self.wait_action)
        state = self.get_state_dict()

        if state is not None:
            reward, done, info = self.calc_reward(state, action)
            logger.log(msg='Reward: ' + str(reward), level=19)
            # info = {'distance': distance}

            state_array = self.state_dic_to_array(state)
            # reward_tensor = torch.tensor(reward)
        else:
            reward = 0
            done = False
            state_array = np.zeros(self.obs_size)
            info = {}

        if done or self.episode_counter >= self.reset_step:
            done = True
            info['episode_'] = {}
            info['episode_']['distance'] = info['distance']
            info['episode_']['phi_r'] = np.asarray(self.phi_r_episode).mean()
            info['bad_transition'] = True

        return state_array, reward, done, info

    def reset(self):
        self.prev_exc = None
        self.episode_counter = 0
        return super().reset()
