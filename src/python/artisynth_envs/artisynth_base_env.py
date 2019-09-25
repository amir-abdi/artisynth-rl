import gym
from common.rest_client import RestClient
import logging
import os
import time
import numpy as np
from gym import spaces

import common.constants as c

logger = logging.getLogger(c.LOGGER_STR)


class ArtiSynthBase(gym.Env):
    def __init__(self, ip, port, init_artisynth, artisynth_model, artisynth_args=''):
        self.observation_space = None
        self.action_space = None
        self.ip = ip
        self.port = port

        if init_artisynth:
            logger.info('Running artisynth')
            self.run_artisynth(ip, port, artisynth_model, artisynth_args)

        self.net = RestClient(ip, port)

    def init_spaces(self, incremental_actions=False):
        # todo: use the same init_spaces for all environments
        action_size = self.get_action_size()
        obs_size = self.get_obs_size()
        state = self.reset()
        state_size = state.shape[0]

        logger.info('State array size: {}'.format(obs_size))
        logger.info('Action array size: {}'.format(action_size))
        state_low, state_high = self.get_state_boundaries(action_size)

        # sanity check
        # assert state_size == obs_size + action_size, \
        #     'The observation size {} and action size {} sent by the environment does not match the state size {}.'.\
        #         format(obs_size, action_size, state_size)
        assert state_size == state_low.shape[0] and state_size == state_high.shape[0], \
            'The shape of state_low {}, state_high {} and state {} do not match.'.format(
                state_low.shape[0], state_high.shape[0], state_size
            )

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

        return action_size, obs_size

    def run_artisynth(self, ip, port, artisynth_model, artisynth_args=''):
        if ip != 'localhost' and ip != '0.0.0.0' and ip != '127.0.0.1':
            raise NotImplementedError('Can\'t initialize ArtiSynth on a remote system.')

        if RestClient.server_is_alive(ip, port):
            return

        command = 'artisynth -model artisynth.models.rl.{} '.format(artisynth_model) + \
                  '[ -port {} {} ] -play -noTimeline'.format(port, artisynth_args)
        command_list = command.split(' ')

        import subprocess
        FNULL = open(os.devnull, 'w')
        subprocess.Popen(command_list, stdout=FNULL, stderr=subprocess.STDOUT)
        # os.spawnvp(os.P_NOWAIT, 'artisynth', tuple(command_list))

        while not RestClient.server_is_alive(ip, port):
            logger.info(f"Waiting for ArtiSynth to launch @{port}")
            time.sleep(3)

    def get_obs_size(self):
        obs_size = self.net.get_post(request_type=c.GET_STR, message=c.OBS_SIZE_STR)
        logger.info('Obs size: {}'.format(obs_size))
        return obs_size

    def get_state_size(self):
        state_size = self.net.get_post(request_type=c.GET_STR, message=c.STATE_SIZE_STR)
        logger.info('State size: {}'.format(state_size))
        return state_size

    def get_action_size(self):
        action_size = self.net.get_post(request_type=c.GET_STR, message=c.ACTION_SIZE_STR)
        logger.info('Action size: {}'.format(action_size))
        return action_size

    def get_state_dict(self):
        state_dict = self.net.get_post(request_type=c.GET_STR, message=c.STATE_STR)
        return state_dict

    def get_excitations_dict(self):
        state_dict = self.net.get_post(request_type=c.GET_STR, message=c.EXCITATIONS_STR)
        return state_dict

    def step(self, action):
        pass

    def reset(self):
        self.net.get_post(request_type=c.GET_STR, message=c.RESET_STR)
        # wait two seconds for ArtiSynth environment to reset
        time.sleep(2.0)
        state_dict = self.get_state_dict()
        return self.state_dic_to_array(state_dict)

    def take_action(self, action):
        action = np.clip(action, c.LOW_EXCITATION, c.HIGH_EXCITATION)
        logger.debug('excitations sent:{}'.format(action))
        next_state_dict = self.net.get_post({c.EXCITATIONS_STR: action.tolist()}, request_type=c.POST_STR,
                                            message=c.EXCITATIONS_STR)
        return next_state_dict

    def render(self, mode=None, close=False):
        pass

    def close(self):
        pass

    def seed(self, seed=None):
        pass

    def state_dic_to_array(self, state_dict):
        pass

    def get_state_boundaries(self, action_size):
        pass
