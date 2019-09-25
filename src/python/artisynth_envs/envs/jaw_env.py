import logging
import time

import numpy as np
import torch
from gym.utils import seeding

from common import constants as c
from common.utilities import Bunch
from artisynth_envs.artisynth_base_env import ArtiSynthBase

logger = logging.getLogger(c.LOGGER_STR)

COMPS_REAL = ['lowerincisor']
COMPS_TARGET = ['lowerincisor_ref']
# PROPS = ['position', 'orientation', 'velocity', 'angularVelocity']
# PROPS = ['position', 'velocity']
PROPS = ['position']

NUM_TARGETS = len(COMPS_TARGET)


class JawEnvV0(ArtiSynthBase):
    def __init__(self, ip, port, wait_action, eval_mode, reset_step,
                 include_current_pos, goal_threshold, incremental_actions, goal_reward,
                 init_artisynth,  **kwargs):
        self.args = Bunch(kwargs)
        super().__init__(ip, port, init_artisynth, 'jaw.RlJawDemo', '-disc false -condyleConstraints true')

        self.prev_exc = None
        self.episode_counter = 0
        self.action_size = 0
        self.obs_size = 0
        self.goal_threshold = float(goal_threshold)

        self.reset_step = int(reset_step)
        self.eval_mode = eval_mode
        self.wait_action = float(wait_action)
        self.include_current_pos = include_current_pos
        self.goal_reward = goal_reward
        self.incremental_actions = incremental_actions

        self.action_size, self.obs_size = self.init_spaces(incremental_actions=self.incremental_actions)

    def state_dict2tensor(self, state):
        return torch.tensor(self.state_dic_to_array(state))

    def get_state_tensor(self):
        state_dict = self.get_state_dict()
        return self.state_dict2tensor(state_dict)

    def step(self, action):
        self.episode_counter += 1

        logger.debug('action:{}'.format(action))

        if self.incremental_actions:
            next_state = self.take_action(action + np.array(self.get_excitations_dict()))
        else:
            next_state = self.take_action(action)

        # todo: next state can potentially be used in a model based approach

        time.sleep(self.wait_action)
        state = self.get_state_dict()

        if state is not None:
            reward, done, info = self.calc_reward(state, action)
            state_array = self.state_dic_to_array(state)
        else:
            reward = 0
            done = False
            state_array = np.zeros(self.obs_size)
            info = {}

        # todo: get rid of this hack!
        if (done or self.episode_counter >= self.reset_step) and not self.eval_mode:
            done = True

        return state_array, reward, done, info

    def calc_reward(self, state, action):
        '''
        This reward is the exact copy of the FDAT solver
        :param state:
        :return:
        '''
        observation = state[c.OBSERVATION_STR]
        props_idx_include = 0

        thres = self.goal_threshold
        info = {}

        phi_u = 0
        for real, target in zip(COMPS_REAL, COMPS_TARGET):
            r = observation[real]
            t = observation[target]
            dist_vec = np.asarray(t[PROPS[props_idx_include]]) - np.asarray(r[PROPS[props_idx_include]])
            phi_u += np.sqrt(np.inner(dist_vec, dist_vec))

        info['distance'] = phi_u
        done = False
        done_reward = 0
        if phi_u < thres:
            done = True
            done_reward = self.goal_reward
            logging.info(f'Done: {phi_u} < {thres}')

        # if decided to add excitaiton regularization
        # excitations = state[c.EXCITATIONS_STR]
        # phi_r = np.inner(excitations, excitations) / 2

        reward = done_reward - phi_u

        logger.log(level=19, msg='reward={}  phi_u={}'.format(reward, phi_u))
        return reward, done, info

    def reset(self):
        self.prev_exc = None
        self.episode_counter = 0

        return super().reset()

    def seed(self, seed=None):
        np_random, seed = seeding.np_random(seed)
        return [seed]

    def state_dic_to_array(self, js):
        logger.debug('state json: %s', str(js))
        observation = js[c.OBSERVATION_STR]
        observation_vector = np.array([])  # np.zeros(self.obs_size)

        if self.include_current_pos:
            for real_object in COMPS_REAL:
                t = observation[real_object]
                for prop in PROPS:
                    observation_vector = np.append(observation_vector, t[prop])

        for target_object in COMPS_TARGET:
            t = observation[target_object]
            for prop in PROPS:
                observation_vector = np.append(observation_vector, t[prop])

        observation_vector = np.append(observation_vector, js[c.EXCITATIONS_STR])
        return np.asarray(observation_vector)

    def get_state_boundaries(self, action_size):
        low = []
        high = []
        # todo: hard coded for now... velocities ignored...
        if self.include_current_pos:
            low.append(0)
            low.append(-95)
            low.append(-80)
            high.append(5)
            high.append(-85)
            high.append(-35)
        low.append(0)
        low.append(-95)
        low.append(-80)
        high.append(5)
        high.append(-85)
        high.append(-35)
        low = np.array(low)
        high = np.array(high)

        low = np.append(low, np.full((action_size,), 0))
        high = np.append(high, np.full((action_size,), 1))
        return low, high

    def render(self, mode='gui', close=False):
        pass