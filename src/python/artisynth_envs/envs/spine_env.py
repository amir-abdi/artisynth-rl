import logging
import numpy as np

from common import constants as c
from common.utilities import Bunch
from artisynth_envs.artisynth_base_env import ArtiSynthBase

logger = logging.getLogger(c.LOGGER_STR)


class SpineEnvV0(ArtiSynthBase):
    def __init__(self, wait_action, reset_step, goal_reward, goal_threshold, **kwargs):
        self.args = Bunch(kwargs)
        super().__init__(**kwargs)

        self.prev_exc = None
        self.episode_counter = 0
        self.reset_step = reset_step
        self.wait_action = wait_action
        self.goal_reward = goal_reward
        self.goal_threshold = goal_threshold

        # misc
        self.phi_r_episode = []
        self.init_spaces()

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

        # todo: switched h from 0.01 to 1... make sure of compatibility with paper
        h = 1
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
            logger.info(F'Done: {phi_u_orig} < {thres}')
            done = True
            done_reward = self.goal_reward

        phi_d = 0
        if self.prev_exc is not None:
            diff_exc = excitations - self.prev_exc
            phi_d = np.linalg.norm(diff_exc) / (2 * h)

        self.prev_exc = excitations

        phi_r = np.linalg.norm(excitations) / 2
        self.phi_r_episode.append(phi_r)

        reward = -(phi_u * w_u + phi_d * w_d + phi_r * w_r) / 10
        reward += done_reward

        logger.log(level=19, msg='{}=-({} + {} + {})/10'.format(reward, phi_u * w_u, phi_d * w_d, phi_r * w_r))
        return reward, done, info

    def step(self, action):
        self.episode_counter += 1

        logger.debug('action:{}'.format(action))
        self.take_action(action)

        self.sleep(self.wait_action)
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
