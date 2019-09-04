import numpy as np
import logging
import time

from rl.core import Space
from rl.core import Processor

from common import constants as c
from artisynth_envs.artisynth_base_env import ArtiSynthBase

logger = logging.getLogger()

COMPS_REAL = ['point']
COMPS_TARGET = ['point_ref']
PROPS = ['position', 'orientation', 'velocity', 'angularVelocity']


class Point2PointEnvV0(ArtiSynthBase):
    def __init__(self, success_thres=0.1,
                 verbose=2, agent=None,
                 include_current_pos=True, wait_action=0,
                 ip='localhost', port=6006,
                 init_artisynth=False, artisynth_model=None, artisynth_args=''):

        super().__init__(ip, port, init_artisynth, artisynth_model, artisynth_args=artisynth_args)

        self.verbose = verbose
        self.success_thres = success_thres
        self.ref_pos = None

        self.agent = agent
        self.include_current_pos = include_current_pos
        self.port = port
        self.prev_distance = None
        self.wait_action = wait_action

        self.action_size = 0
        self.obs_size = 0

        self.init_spaces()

    def init_spaces(self):
        self.action_size = self.get_action_size()
        obs = self.reset()
        self.obs_size = obs.shape[0]
        logger.info('State size: {}   action_size: {}'.format(obs.shape, self.action_size))

        self.observation_space = type(self).ObservationSpace(self.obs_size)  # np.random.rand(dof_observation)
        self.action_space = type(self).ActionSpace(self.action_size)

    def set_state(self, state):
        self._set_state(state[:3], state[4:])

    def _set_state(self, ref_pos, follower_pos):
        self.ref_pos = ref_pos
        self.follower_pos = follower_pos

    def state_dic_to_array(self, state_dict: dict):
        observation = state_dict[c.OBSERVATION_STR]
        observation_vector = np.array([])  # np.zeros(self.obs_size)

        # only include position
        props_idx_include = 0

        if self.include_current_pos:
            for real_object in COMPS_REAL:
                t = observation[real_object]
                observation_vector = np.append(observation_vector, t[PROPS[props_idx_include]])

        for target_object in COMPS_TARGET:
            t = observation[target_object]
            observation_vector = np.append(observation_vector, t[PROPS[props_idx_include]])

        return np.asarray(observation_vector)

    def reset(self):
        self.ref_pos = None
        self.prev_distance = None
        logger.info('Reset')

        return super().reset()

    def render(self, mode='human', close=False):
        # our environment does not need rendering
        pass

    def seed(self, seed=None):
        np.random.seed(seed)

    def configure(self, *args, **kwargs):
        pass

    def calculate_reward_pos(self, ref_pos, follow_pos, exp=True, constant=1):
        distance = type(self).calculate_distance(ref_pos, follow_pos)
        if distance < self.success_thres:
            # achieved done state
            return 1, True
        else:
            if exp:
                return constant * np.exp(-distance) - 50, False
            else:
                return constant / (distance + c.EPSILON), False

    @staticmethod
    def calculate_distance(a, b):
        return np.sqrt(np.sum((b - a) ** 2))

    def step(self, action):
        logger.debug('action:{}'.format(action))
        self.take_action(action)
        time.sleep(self.wait_action)

        state = self.get_state_dict()
        obs = state[c.OBSERVATION_STR]
        if state is not None:
            new_ref_pos = np.asarray(obs[COMPS_TARGET[0]][PROPS[0]])
            new_follower_pos = np.asarray(obs[COMPS_REAL[0]][PROPS[0]])

            distance = self.calculate_distance(new_ref_pos, new_follower_pos)
            if self.prev_distance is not None:
                reward, done = self.calculate_reward_time_5(distance,
                                                            self.prev_distance)  # r4
            else:
                reward, done = (0, False)
            self.prev_distance = distance
            if done:
                logger.info('Achieved done state')
            logger.info('Reward: ' + str(reward))

            state_arr = self.state_dic_to_array(state)
            info = {'distance': distance}

        return state_arr, reward, done, info

    def calculate_reward_move(self, ref_pos, prev_follow_pos,
                              new_follow_pos):  # r1
        prev_dist = type(self).calculate_distance(ref_pos, prev_follow_pos)

        new_dist = type(self).calculate_distance(ref_pos, new_follow_pos)
        if new_dist < self.success_thres:
            # Achieved done state
            return 1, True
        else:
            if prev_dist - new_dist > 0:
                return np.sign(
                    prev_dist - new_dist) * self.calculate_reward_pos(
                    ref_pos,
                    new_follow_pos,
                    False,
                    10), False
            else:
                return np.sign(
                    prev_dist - new_dist) * self.calculate_reward_pos(
                    ref_pos,
                    new_follow_pos,
                    False,
                    10), False

    def calculate_reward_time_n5(self, new_dist, prev_dist):  # r2
        if new_dist < self.success_thres:
            # achieved done state
            return 5 / self.agent.episode_step, True
        else:
            if prev_dist - new_dist > 0:
                return 1 / self.agent.episode_step, False
            else:
                return -1, False

    def calculate_reward_time_dist_n5(self, new_dist, prev_dist):  # r3
        if new_dist < self.success_thres:
            # achieved done state
            return 5 / self.agent.episode_step, True
        else:
            if prev_dist - new_dist > 0:
                return 1 / (self.agent.episode_step * new_dist), False
            else:
                return -1, False

    def calculate_reward_time_5(self, new_dist, prev_dist):  # r4
        if new_dist < self.success_thres:
            # achieved done state
            return 5, True
        else:
            if prev_dist - new_dist > 0:
                return 1 / self.agent.episode_step, False
            else:
                return -1, False

    def calculate_reward_time_dist_nn5(self, new_dist, prev_dist):  # r5
        if new_dist < self.success_thres:
            # achieved done state
            return 5 / (self.agent.episode_step * new_dist), True
        else:
            if prev_dist - new_dist > 0:
                return 1 / (self.agent.episode_step * new_dist), False
            else:
                return -1, False

    class ActionSpace(Space):
        def __init__(self, num_muscles):
            self.dof_action = num_muscles
            self.shape = (self.dof_action,)

        def sample(self, seed=None):
            if seed is not None:
                np.random.seed(seed)
            values = np.random.rand(self.dof_action)
            return values

        def contains(self, x):
            if x.ndim != 1:
                return False
            if x.shape[0] != self.dof_action:
                return False
            if np.max(x) > 1 or np.min(x) < 0:
                return False
            return True

    class ObservationSpace(Space):
        def __init__(self, dof_obs=6, radius=4.11):
            self.shape = (dof_obs,)
            self.dof_obs = dof_obs
            self.radius = radius

        def sample(self, seed=None):
            if seed is not None:
                np.random.seed(seed)
            return (np.random.rand(self.dof_obs) - 0.5) * self.radius * 2

        def contains(self, x):
            if x.ndim != 1:
                return False
            if x.shape[0] != self.dof_obs:
                return False
            if np.max(x) > self.radius or np.min(x) < -self.radius:
                return False
            return True


class PointModel2dProcessor(Processor):
    """Abstract base class for implementing processors.
        A processor acts as a coupling mechanism between an `Agent` and its `Env`. This can
        be necessary if your agent has different requirements with respect to the form of the
        observations, actions, and rewards of the environment. By implementing a custom processor,
        you can effectively translate between the two without having to change the underlaying
        implementation of the agent or environment.
        Do not use this abstract base class directly but instead use one of the concrete implementations
        or write your own.
        """

    def process_step(self, observation, reward, done, info):
        """Processes an entire step by applying the processor to the observation, reward, and info arguments.
        # Arguments
            observation (object): An observation as obtained by the environment.
            reward (float): A reward as obtained by the environment.
            done (boolean): `True` if the environment is in a terminal state, `False` otherwise.
            info (dict): The debug info dictionary as obtained by the environment.
        # Returns
            The tupel (observation, reward, done, reward) with with all elements after being processed.
        """
        observation = self.process_observation(observation)
        reward = self.process_reward(reward)
        info = self.process_info(info)
        return observation, reward, done, info

    def process_observation(self, observation):
        """Processes the observation as obtained from the environment for use in an agent and
        returns it.
        """
        return observation

    def process_reward(self, reward):
        """Processes the reward as obtained from the environment for use in an agent and
        returns it.
        """
        return reward

    def process_info(self, info):
        """Processes the info as obtained from the environment for use in an agent and
        returns it.
        """
        return info

    def process_action(self, action):
        """Processes an action predicted by an agent but before execution in an environment.
        """
        return action

    def process_state_batch(self, batch):
        """Processes an entire batch of states and returns it.
        """
        return batch

    @property
    def metrics(self):
        """The metrics of the processor, which will be reported during training.
        # Returns
            List of `lambda y_true, y_pred: metric` functions.
        """
        return []

    @property
    def metrics_names(self):
        """The human-readable names of the agent's metrics. Must return as many names as there
        are metrics (see also `compile`).
        """
        return []
