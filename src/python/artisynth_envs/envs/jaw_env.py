import logging
import time

import numpy as np
import torch
from gym import spaces
from gym.utils import seeding

from common import constants as c
from common.utilities import Bunch
from artisynth_envs.artisynth_base_env import ArtiSynthBase

logger = logging.getLogger()

COMPS_REAL = ['jaw']
COMPS_TARGET = ['jaw_ref']
PROPS = ['position', 'orientation', 'velocity', 'angularVelocity']

NUM_TARGETS = len(COMPS_TARGET)


class JawEnvV0(ArtiSynthBase):
    def __init__(self, ip, port, wait_action, eval_mode, reset_step,
                 include_current_pos, init_artisynth=True, **kwargs):
        self.args = Bunch(kwargs)
        super().__init__(ip, port, init_artisynth, self.args.artisynth_model)

        self.prev_exc = None
        self.episode_counter = 0
        self.reset_step = reset_step
        self.eval_mode = eval_mode
        self.wait_action = wait_action
        self.include_current_pos = include_current_pos

        self.action_size = 0
        self.obs_size = 0

        self.init_spaces()

    def init_spaces(self):
        self.action_size = self.get_action_size()
        obs = self.reset()
        logger.info('State array size: {}'.format(obs.shape))
        self.obs_size = obs.shape[0]

        self.observation_space = spaces.Box(low=-0.2, high=+0.2,
                                            shape=[self.obs_size], dtype=np.float32)
        self.observation_space.shape = (self.obs_size,)
        self.action_space = spaces.Box(low=c.LOW_EXCITATION, high=c.HIGH_EXCITATION,
                                       shape=(self.action_size,),
                                       dtype=np.float32)

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
        props_idx_include = 1

        thres = self.args.goal_threshold
        info = {'distance': 0,
                'vel': 0}

        h = 0.01
        w_u = self.args.w_u
        w_d = self.args.w_d
        w_r = self.args.w_r
        w_u *= w_u

        phi_u = 0
        for real, target in zip(COMPS_REAL, COMPS_TARGET):
            r = observation[real]
            t = observation[target]
            dist_vec = np.asarray(t[PROPS[props_idx_include]]) - np.asarray(r[PROPS[props_idx_include]])
            phi_u += (np.inner(dist_vec, dist_vec))

        phi_u_orig = phi_u
        phi_u /= (2 * h)

        info['distance'] = phi_u_orig
        done = False
        done_reward = 0

        if phi_u_orig < thres:
            if self.args.goal_terminal:
                done = True
            done_reward = self.args.goal_reward

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
        self.take_action(action + np.array(self.get_excitations_dict()))

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

        # todo: get rid of this hack!
        # not self.eval_mode and
        if (done or self.episode_counter >= self.reset_step) and not self.eval_mode:
            done = True
            info['episode_'] = {}
            info['episode_']['distance'] = info['distance']
            info['episode_']['phi_r'] = np.asarray(self.phi_r_episode).mean()

        return state_array, reward, done, info

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
        # print('observation_vector ', observation_vector )
        return np.asarray(observation_vector)

    def render(self, mode='gui', close=False):
        pass
