import numpy as np
import os

from rl.core import Env
from rl.core import Space
from rl.core import Processor

from python.common import begin_time
from python.common import config as config
from python.common import constants as c
from python.common import Net

EPSILON = 1E-12


class PointModel2dEnv(Env):
    def __init__(self, muscle_labels=None, dof_observation=6, success_thres=0.1,
                 verbose=2, log_to_file=True, log_file='log', agent=None,
                 include_follow=True, ip='localhost', port=6006):
        self.net = Net(ip, port)

        self.verbose = verbose
        self.success_thres = success_thres
        self.ref_pos = None

        self.action_space = type(self).ActionSpace(muscle_labels)
        self.observation_space = type(self).ObservationSpace(
            dof_observation)  # np.random.rand(dof_observation)
        self.log_to_file = log_to_file
        self.log_file_name = log_file
        if log_to_file:
            self.logfile, path = type(self).create_log_file(log_file)
            self.log('Logging into file: ' + path, verbose=1)
        self.agent = agent
        self.include_follow = include_follow
        self.port = port
        self.prev_distance = None
        self.muscle_labels = muscle_labels

    @staticmethod
    def create_log_file(log_file):
        log_folder = config.env_log_directory
        path = os.path.join(log_folder, (log_file + begin_time))
        return open(str(path), "w"), str(path)

    def log(self, obj, verbose=2, same_line=False):
        if self.log_to_file and not self.logfile.closed:
            self.logfile.writelines(str(obj))
            self.logfile.write("\n")

        if same_line:
            print(obj, sep=' ', end='\r',
                  flush=True) if verbose <= self.verbose else lambda: None
            print(obj, sep=' ', end=' ',
                  flush=True) if verbose <= self.verbose else lambda: None
        else:
            print(obj) if verbose <= self.verbose else lambda: None

    def augment_action(self, action):
        return dict(zip(self.muscle_labels, np.nan_to_num(action)))

    def set_state(self, state):
        self.set_state(state[:3], state[4:])

    def set_state(self, ref_pos, follower_pos):
        self.ref_pos = ref_pos
        self.follower_pos = follower_pos

    @staticmethod
    def parse_state(state_dict: dict):
        state = {'ref_pos': np.array(
            [float(s) for s in state_dict['ref_pos'].split(" ")]),
            'follow_pos': np.array(
                [float(s) for s in state_dict['follow_pos'].split(" ")])}
        return state

    def state_json_to_array(self, state_dict: dict):
        state_arr = np.asarray(state_dict['ref_pos'])
        if self.include_follow:
            state_arr = np.concatenate((state_arr, state_dict['follow_pos']))
        return state_arr

    def reset(self):
        self.net.get_post(request_type=c.RESET_STR)
        self.ref_pos = None
        self.prev_distance = None
        self.log('Reset', verbose=0)
        state_dict = self.get_state_dict()
        state = self.state_json_to_array(state_dict)
        return state

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
                return constant / (distance + EPSILON), False

    def get_state_dict(self):
        self.net.get_post(request_type=c.GET_STATE_STR)
        state_dict = self.net.receive_message(c.STATE_STR, retry_type=c.GET_STATE_STR)
        return state_dict

    @staticmethod
    def calculate_distance(a, b):
        return np.sqrt(np.sum((b - a) ** 2))

    def step(self, action):
        action = self.augment_action(action)
        # self.net.send(action, 'excitations')
        self.net.get_post({'excitations': action}, request_type='setExcitations')

        # time.sleep(0.3)
        state = self.get_state_dict()
        if state is not None:
            new_ref_pos = np.asarray(state['ref_pos'])
            new_follower_pos = np.asarray(state['follow_pos'])

            distance = self.calculate_distance(new_ref_pos, new_follower_pos)
            if self.prev_distance is not None:
                reward, done = self.calcualte_reward_time_5(distance,
                                                            self.prev_distance)  # r4
            else:
                reward, done = (0, False)
            self.prev_distance = distance
            if done:
                self.log('Achieved done state', verbose=0)
            self.log('Reward: ' + str(reward), verbose=1, same_line=True)

            state_arr = self.state_json_to_array(state)
            info = {'distance': distance}

        return state_arr, reward, done, info

    def calcualte_reward_move(self, ref_pos, prev_follow_pos,
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

    def calcualte_reward_time_n5(self, new_dist, prev_dist):  # r2
        if new_dist < self.success_thres:
            # achieved done state
            return 5 / self.agent.episode_step, True
        else:
            if prev_dist - new_dist > 0:
                return 1 / self.agent.episode_step, False
            else:
                return -1, False

    def calcualte_reward_time_dist_n5(self, new_dist, prev_dist):  # r3
        if new_dist < self.success_thres:
            # achieved done state
            return 5 / self.agent.episode_step, True
        else:
            if prev_dist - new_dist > 0:
                return 1 / (self.agent.episode_step * new_dist), False
            else:
                return -1, False

    def calcualte_reward_time_5(self, new_dist, prev_dist):  # r4
        if new_dist < self.success_thres:
            # achieved done state
            return 5, True
        else:
            if prev_dist - new_dist > 0:
                return 1 / self.agent.episode_step, False
            else:
                return -1, False

    def calcualte_reward_time_dist_nn5(self, new_dist, prev_dist):  # r5
        if new_dist < self.success_thres:
            # achieved done state
            return 5 / (self.agent.episode_step * new_dist), True
        else:
            if prev_dist - new_dist > 0:
                return 1 / (self.agent.episode_step * new_dist), False
            else:
                return -1, False

    class ActionSpace(Space):
        def __init__(self, muscle_labels):
            self.dof_action = len(muscle_labels)
            self.shape = (self.dof_action,)
            self.muscle_labels = muscle_labels

        def sample(self, seed=None):
            if seed is not None:
                np.random.seed(seed)
            values = np.random.rand(self.dof_action)
            return dict(zip(self.muscle_labels, values))

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
