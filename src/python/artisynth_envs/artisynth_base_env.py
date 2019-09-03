import gym
from common.rest_client import RestClient
import logging
import os
import time
import numpy as np

import common.constants as c

logger = logging.getLogger()


class ArtisynthBase(gym.Env):
    def __init__(self, ip, port, init_artisynth, artisynth_model, artisynth_args=''):
        self.observation_space = None
        self.action_space = None
        self.ip = ip
        self.port = port

        if init_artisynth:
            logger.info('Running artisynth')
            self.run_artisynth(ip, port, artisynth_model, artisynth_args)

        self.net = RestClient(ip, port)

    def run_artisynth(self, ip, port, artisynth_model, artisynth_args=''):
        if ip != 'localhost' and ip != '0.0.0.0' and ip != '127.0.0.1':
            raise NotImplementedError('Can\'t initialize ArtiSynth on a remote system.')

        if RestClient.server_is_alive(ip, port):
            return

        command = 'artisynth -model artisynth.models.rl.{} '.format(artisynth_model) + \
                  '[ -port {} {} ] -play -noTimeline'. \
                      format(port, artisynth_args)
        command_list = command.split(' ')

        import subprocess
        FNULL = open(os.devnull, 'w')
        subprocess.Popen(command_list, stdout=FNULL, stderr=subprocess.STDOUT)
        while not RestClient.server_is_alive(ip, port):
            logger.info("Waiting for ArtiSynth to launch")
            time.sleep(3)

    def get_state_size(self):
        rec_dict = self.net.get_post(request_type=c.GET_STR, message=c.STATE_SIZE_STR)
        logger.info('State size: {}'.format(rec_dict[c.STATE_SIZE_STR]))
        return rec_dict[c.STATE_SIZE_STR]

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
        state_dict = self.get_state_dict()
        return self.state_dic_to_array(state_dict)

    def take_action(self, action):
        action = np.clip(action, c.LOW_EXCITATION, c.HIGH_EXCITATION)
        self.net.get_post({c.EXCITATIONS_STR: action.tolist()}, request_type=c.POST_STR, message=c.EXCITATIONS_STR)

    def render(self, mode=None, close=False):
        pass

    def close(self):
        pass

    def seed(self, seed=None):
        pass

    def state_dic_to_array(self, state_dict):
        pass