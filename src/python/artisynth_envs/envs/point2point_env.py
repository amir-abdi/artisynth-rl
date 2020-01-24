import numpy as np

from common import constants as c
from common.config import setup_logger
from artisynth_envs.artisynth_base_env import ArtiSynthBase

logger = setup_logger()


class Point2PointEnv(ArtiSynthBase):
    def __init__(self, goal_threshold, wait_action, reset_step, goal_reward, **kwargs):
        super().__init__(**kwargs)

        self.goal_threshold = goal_threshold
        self.prev_distance = None
        self.wait_action = wait_action

        self.episode_counter = 0
        self.reset_step = int(reset_step)
        self.goal_reward = goal_reward

        self.position_radius = self.get_radius(kwargs['artisynth_args'])
        self.init_spaces()

    @staticmethod
    def get_radius(args_str):
        return float(args_str.split('radius ')[1])

    def get_state_boundaries(self, action_size):
        low = []
        high = []
        if self.include_current_state:
            for i in range(3):
                low.append(-self.position_radius)
                high.append(self.position_radius)
        for i in range(3):
            low.append(-self.position_radius)
            high.append(self.position_radius)
        low = np.array(low)
        high = np.array(high)
        if self.include_current_excitations:
            low = np.append(low, np.full((action_size,), 0))
            high = np.append(high, np.full((action_size,), 1))
        return low, high

    def reset(self):
        self.prev_distance = None
        logger.info('Reset')
        return super().reset()

    def configure(self, *args, **kwargs):
        pass

    def step(self, action):
        logger.debug('action:{}'.format(action))
        self.episode_counter += 1
        self.take_action(action)
        self.sleep(self.wait_action)

        state = self.get_state_dict()
        if not state:
            return None, 0, False, {}
        obs = state[c.OBSERVATION_STR]
        distance = self.distance_to_target(obs)
        reward, done, info = self.calculate_reward(distance, self.prev_distance)
        self.prev_distance = distance
        state_arr = self.state_dic_to_array(state)

        if self.episode_counter >= self.reset_step:
            done = True

        return state_arr, reward, done, info

    def calculate_reward(self, new_dist, prev_dist):
        if not prev_dist:
            return 0, False, {}
        done = False
        info = {'distance': new_dist}
        if new_dist < self.goal_threshold:
            done = True
            reward = 5
            logger.log(msg='Achieved done state', level=18)
        else:
            if prev_dist - new_dist > 0:
                reward = 1 / self.episode_counter
            else:
                reward = -1

        logger.log(msg='Reward: ' + str(reward), level=18)
        return reward, done, info
