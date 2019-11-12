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

    def distance_to_target(self, observation):
        diff = 0
        for current_comp, target_comp in zip(self.components[c.CURRENT], self.components[c.TARGET]):
            for prop in self.components[c.PROPS]:
                if prop == 'velocity':  # ignore velocity in the reward
                    continue
                p_current = np.asarray(observation[current_comp[c.NAME]][prop])
                p_target = np.asarray(observation[target_comp[c.NAME]][prop])
                diff += np.linalg.norm(p_current - p_target)
        return diff

    def get_velocity(self, observation):
        diff = 0
        for current_comp, target_comp in zip(self.components[c.CURRENT], self.components[c.TARGET]):
            prop = 'velocity'
            p_current = np.asarray(observation[current_comp[c.NAME]][prop])
            p_target = np.asarray(observation[target_comp[c.NAME]][prop])
            diff += np.linalg.norm(p_current - p_target)
        return diff

    def calc_reward(self, state, action):
        observation = state[c.OBSERVATION_STR]
        # excitations = state[c.EXCITATIONS_STR]
        muscle_forces = state[c.MUSCLE_FORCES_STR]

        info = {}
        done = False
        done_reward = 0
        eps = 0.0000001

        velocity = self.get_velocity(observation)
        phi_u = self.distance_to_target(observation)
        # todo: the velocity might be removed... hard coded for now
        if phi_u < self.goal_threshold and velocity < 3:
            done = True
            done_reward = self.goal_reward
            logging.info(f'Done: {phi_u} < {self.goal_threshold}')

        if self.args.hack_muscle_forces:
            phi_r = np.linalg.norm(muscle_forces)
            info['muscleForces'] = phi_r
        else:
            excitations = action
            phi_r = np.mean(excitations)  # phi_r = np.linalg.norm(excitations)
            info['excitations'] = phi_r

        # todo: remove this hack
        if self.args.hack_log:
            reward = done_reward - \
                     self.w_u * np.log10(phi_u + eps) - phi_r * self.w_r
        else:
            reward = done_reward - np.clip(((phi_u + eps) ** self.pow_u) * self.w_u,
                                           a_min=-200, a_max=float('inf')) - \
                     phi_r * self.w_r

        info['distance'] = phi_u
        logger.log(level=18, msg='reward={}  phi_u={}   phi_r={}'.format(reward, phi_u, phi_r))

        return reward, done, info

    def reset(self):
        self.episode_counter = 0
        return super().reset()
