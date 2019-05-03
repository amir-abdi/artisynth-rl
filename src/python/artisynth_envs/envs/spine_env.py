import logging
import os
import time

import gym
import numpy as np
import torch
from gym import spaces
from gym.utils import seeding

from common import constants as c
from common.rest_client import RestClient
from common.utilities import Bunch

logger = logging.getLogger()

COMPS_REAL = ['thorax', 'L1', 'L2', 'L3', 'L4', 'L5']
COMPS_TARGET = ['thorax_ref', 'L1_ref', 'L2_ref', 'L3_ref', 'L4_ref', 'L5_ref']
PROPS = ['position', 'orientation', 'velocity', 'angularVelocity']

NUM_TARGETS = len(COMPS_TARGET)


class SpineEnvV0(gym.Env):
    def __init__(self, ip, port, wait_action, eval_mode, reset_step,
                 init_artisynth, include_current_pos, **kwargs):

        self.prev_exc = None
        self.args = Bunch(kwargs)
        self.episode_counter = 0
        self.reset_step = reset_step
        self.eval_mode = eval_mode
        self.wait_action = wait_action
        self.include_current_pos = include_current_pos
        self.ip = ip
        self.port = port

        self.action_size = 0
        self.obs_size = 0
        self.observation_space = None
        self.action_space = None

        if init_artisynth:
            logger.info('Running artisynth')
            self.run_artisynth(ip, port)

        self.net = RestClient(ip, port)
        self.init_spaces()

    def init_spaces(self):
        self.action_size = self.get_action_size()
        obs = self.reset()
        logger.info('State array size: {}'.format(obs.shape))
        self.obs_size = obs.shape[0]

        self.observation_space = spaces.Box(low=-0.2, high=+0.2,
                                            shape=[self.obs_size], dtype=np.float32)
        self.observation_space.shape = (self.obs_size,)
        # init action space
        self.action_space = spaces.Box(low=c.LOW_EXCITATION, high=c.HIGH_EXCITATION,
                                       shape=(self.action_size,),
                                       dtype=np.float32)

    def run_artisynth(self, ip, port):
        if ip != 'localhost' and ip != '0.0.0.0' and ip != '127.0.0.1':
            raise NotImplementedError('Can\'t initialize ArtiSynth on a remote system.')

        if RestClient.server_is_alive(self.ip, self.port):
            return

        command = 'artisynth -model artisynth.models.rl.RlLumbarSpineModel ' + \
                  '[ -port {} ] -play -noTimeline'. \
                      format(port)
        command_list = command.split(' ')

        import subprocess
        FNULL = open(os.devnull, 'w')
        subprocess.Popen(command_list, stdout=FNULL, stderr=subprocess.STDOUT)
        while not RestClient.server_is_alive(self.ip, self.port):
            logger.info("Waiting for ArtiSynth to launch")
            time.sleep(3)

    def get_state_dict(self):
        state_dict = self.net.get_post(request_type=c.GET_STR, message=c.STATE_STR)
        return state_dict

    def get_state_size(self):
        rec_dict = self.net.get_post(request_type=c.GET_STR, message=c.STATE_SIZE_STR)
        logger.info('State size: {}'.format(rec_dict[c.STATE_SIZE_STR]))
        return rec_dict[c.STATE_SIZE_STR]

    def get_action_size(self):
        action_size = self.net.get_post(request_type=c.GET_STR, message=c.ACTION_SIZE_STR)
        logger.info('Action size: {}'.format(action_size))
        return action_size

    def state_dict2tensor(self, state):
        return torch.tensor(self.state_dic_to_array(state))

    def get_state_tensor(self):
        state_dict = self.get_state_dict()
        return self.state_dict2tensor(state_dict)

    def take_action(self, action):
        action = np.clip(action, c.LOW_EXCITATION, c.HIGH_EXCITATION)
        self.net.get_post({c.EXCITATIONS_STR: action.tolist()}, request_type=c.POST_STR, message=c.EXCITATIONS_STR)

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

        # todo: get rid of this hack!
        # not self.eval_mode and
        if (done or self.episode_counter >= self.reset_step) and not self.eval_mode:
            done = True
            info['episode_'] = {}
            info['episode_']['distance'] = info['distance']
            info['episode_']['phi_r'] = np.asarray(self.phi_r_episode).mean()

        return state_array, reward, done, info

    def reset(self):
        self.net.get_post(request_type=c.GET_STR, message=c.RESET_STR)
        state_dict = self.get_state_dict()
        self.prev_exc = None
        self.episode_counter = 0

        return self.state_dic_to_array(state_dict)

    def seed(self, seed=None):
        self.np_random, seed = seeding.np_random(seed)
        return [seed]

    def state_dic_to_array(self, js):
        logger.debug('state json: %s', str(js))
        observation = js['observation']
        observation_vector = np.array([])  # np.zeros(self.obs_size)

        # only include orientation
        props_idx_include = 1

        if self.include_current_pos:
            for real_object in COMPS_REAL:
                t = observation[real_object]
                observation_vector = np.append(observation_vector, t[PROPS[props_idx_include]])

        for target_object in COMPS_TARGET:
            t = observation[target_object]
            observation_vector = np.append(observation_vector, t[PROPS[props_idx_include]])
            # print('observation_vector ', observation_vector)

        # todo: include excitations in the observation vector

        return np.asarray(observation_vector)

    def render(self, mode='gui', close=False):
        pass
