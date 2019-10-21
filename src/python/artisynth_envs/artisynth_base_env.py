import logging
import os
import time
import numpy as np
import subprocess
import gym
from gym import spaces
from gym.utils import seeding
from abc import ABC, abstractmethod

from common.rest_client import RestClient
import common.constants as c

logger = logging.getLogger(c.LOGGER_STR)


class ArtiSynthBase(gym.Env, ABC):
    def __init__(self, ip, port, artisynth_model, test, components, zero_excitations_on_reset,
                 include_current_excitations, include_current_state, w_u, w_d, w_r, seed, incremental_actions,
                 artisynth_args='', **kwargs):
        logger.warning(f'The following args MIGHT have remained unused: {kwargs}')

        self.observation_space = None
        self.action_space = None
        self.ip = ip
        self.port = port
        self.test_mode = test

        self.include_current_excitations = include_current_excitations
        self.include_current_state = include_current_state
        self.incremental_actions = incremental_actions

        self.w_u = w_u  # position
        self.w_d = w_d  # temporal damping
        self.w_r = w_r  # excitation regularization

        self.components = None
        self.action_size = 0
        self.obs_size = 0
        self.components = components
        self.zero_excitations_on_reset = zero_excitations_on_reset

        self.net = RestClient(ip, port)
        if not RestClient.server_is_alive(ip, port):  # if server is not already running, initiate ArtiSynth
            self.run_artisynth(ip, port, artisynth_model, artisynth_args)
        self.seed(seed)

    def init_spaces(self, incremental_actions=False):
        # todo: use the same init_spaces for all environments
        action_size = self.get_action_size()
        obs_size = self.get_obs_size()
        state = self.reset()
        state_size = state.shape[0]

        state_low, state_high = self.get_state_boundaries(action_size)

        # sanity check
        # assert state_size == obs_size + action_size, \
        #     'The observation size {} and action size {} sent by the environment does not match the state size {}.'.\
        #         format(obs_size, action_size, state_size)
        assert state_size == state_low.shape[0] and state_size == state_high.shape[0], \
            f'The shape of state_low ({state_low.shape[0]}), ' \
            f'state_high ({state_high.shape[0]}) and state_size ({state_size}) do not match.'

        self.observation_space = spaces.Box(low=state_low, high=state_high, dtype=np.float32)
        self.observation_space.shape = (state_size,)
        if incremental_actions:
            low_action = c.LOW_EXCITATION_INC
            high_action = c.HIGH_EXCITATION_INC
        else:
            low_action = c.LOW_EXCITATION
            high_action = c.HIGH_EXCITATION
        self.action_space = spaces.Box(low=low_action, high=high_action,
                                       shape=(action_size,), dtype=np.float32)

        logger.info('Env observation size (excluding excitations): {}'.format(obs_size))
        logger.info('Action array size: {}'.format(action_size))
        logger.info('State size: {}'.format(state_size))

        return action_size, obs_size

    def get_state_boundaries(self, action_size):
        low = []
        high = []
        # order: lateral, anteroposterio, vertical
        if self.include_current_state:
            for current_obj in self.components[c.CURRENT]:
                low.extend(current_obj[c.LOW])
                high.extend(current_obj[c.HIGH])
        for target_obj in self.components[c.TARGET]:
            low.extend(target_obj[c.LOW])
            high.extend(target_obj[c.HIGH])
        low = np.array(low)
        high = np.array(high)

        if self.include_current_excitations:
            low = np.append(low, np.full((action_size,), c.LOW_EXCITATION))
            high = np.append(high, np.full((action_size,), c.HIGH_EXCITATION))
        return low, high

    def run_artisynth(self, ip, port, artisynth_model, artisynth_args=''):
        if ip != 'localhost' and ip != '0.0.0.0' and ip != '127.0.0.1':
            raise NotImplementedError('Can\'t initialize ArtiSynth on a remote system.')

        if RestClient.server_is_alive(ip, port):
            return

        command = 'artisynth -model {} '.format(artisynth_model) + \
                  '[ -port {} {} ] -play -noTimeline'.format(port, artisynth_args)
        command_list = command.split(' ')

        FNULL = open(os.devnull, 'w')
        subprocess.Popen(command_list, stdout=FNULL, stderr=subprocess.STDOUT)
        # os.spawnvp(os.P_NOWAIT, 'artisynth', tuple(command_list))

        while not RestClient.server_is_alive(ip, port):
            logger.info(f"Waiting for ArtiSynth to launch @{port}")
            time.sleep(3)

    def get_obs_size(self):
        obs_size = self.net.get_post(request_type=c.GET_STR, message=c.OBS_SIZE_STR)
        return obs_size

    def get_state_size(self):
        state_size = self.net.get_post(request_type=c.GET_STR, message=c.STATE_SIZE_STR)
        return state_size

    def get_action_size(self):
        action_size = self.net.get_post(request_type=c.GET_STR, message=c.ACTION_SIZE_STR)
        return action_size

    def get_state_dict(self):
        state_dict = self.net.get_post(request_type=c.GET_STR, message=c.STATE_STR)
        return state_dict

    def get_excitations_dict(self):
        state_dict = self.net.get_post(request_type=c.GET_STR, message=c.EXCITATIONS_STR)
        return state_dict

    def step(self, action):
        pass

    def reset(self, set_excitations_zero=None):
        # Let the environment to override zero_excitations_on_reset if needed for particular reset commands
        if set_excitations_zero is None:
            set_excitations_zero = self.zero_excitations_on_reset

        self.net.get_post(set_excitations_zero, request_type=c.POST_STR, message=c.RESET_STR)

        # wait two seconds for ArtiSynth environment to reset
        time.sleep(1.0)

        state_dict = self.get_state_dict()
        return self.state_dic_to_array(state_dict)

    def take_action(self, action):
        action = np.clip(action, c.LOW_EXCITATION, c.HIGH_EXCITATION)
        logger.debug('excitations sent:{}'.format(action))
        next_state_dict = self.net.get_post({c.EXCITATIONS_STR: action.tolist()}, request_type=c.POST_STR,
                                            message=c.EXCITATIONS_STR)
        return next_state_dict

    def state_dic_to_array(self, js):
        logger.debug('state json: %s', str(js))
        observation = js[c.OBSERVATION_STR]
        observation_vector = np.array([])  # np.zeros(self.obs_size)

        if self.include_current_state:
            for current_comp in self.components[c.CURRENT]:
                t = observation[current_comp[c.NAME]]
                for prop in self.components[c.PROPS]:
                    observation_vector = np.append(observation_vector, t[prop])

        for target_comp in self.components[c.TARGET]:
            t = observation[target_comp[c.NAME]]
            for prop in self.components[c.PROPS]:
                observation_vector = np.append(observation_vector, t[prop])

        if self.include_current_excitations:
            observation_vector = np.append(observation_vector, js[c.EXCITATIONS_STR])

        return np.asarray(observation_vector)

    def distance_to_target(self, observation):
        diff = 0
        for current_comp, target_comp in zip(self.components[c.CURRENT], self.components[c.TARGET]):
            for prop in self.components[c.PROPS]:
                p_current = np.asarray(observation[current_comp[c.NAME]][prop])
                p_target = np.asarray(observation[target_comp[c.NAME]][prop])
                diff += np.linalg.norm(p_current - p_target)
        return diff

    def seed(self, seed=None):
        # todo [BUG]: seeding gives different (but consistent) results when running artisynth seprately vs. from python.
        np_random, seed = seeding.np_random(seed)
        self.net.get_post(seed, request_type=c.POST_STR, message=c.SET_SEED_STR)
        return [seed]

    def wrap_action(self, action):
        if self.incremental_actions:
            # todo: MAYBE get excitations from previous state not by calling the environment again!
            current_excitations = np.array(self.get_excitations_dict())
            return np.clip(action + current_excitations, a_min=c.LOW_EXCITATION, a_max=c.HIGH_EXCITATION)
        return action
