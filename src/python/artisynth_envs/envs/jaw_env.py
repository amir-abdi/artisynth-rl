import numpy as np
import torch
import os

from common import constants as c
from common.utilities import Bunch
from common.config import setup_logger
from artisynth_envs.artisynth_base_env import ArtiSynthBase

logger = setup_logger()


class JawEnv(ArtiSynthBase):
    def __init__(self, wait_action, reset_step, goal_threshold, goal_reward, **kwargs):
        self.args = Bunch(kwargs)
        super().__init__(**kwargs)

        self.episode_counter = 0
        self.action_size = 0
        self.obs_size = 0
        self.goal_threshold = float(goal_threshold)

        self.reset_step = int(reset_step)
        self.wait_action = float(wait_action)

        self.goal_reward = goal_reward

        self.action_size, self.obs_size = self.init_spaces(
            incremental_actions=self.incremental_actions)

    def state_dict2tensor(self, state):
        return torch.tensor(self.state_dic_to_array(state))

    def get_state_tensor(self):
        state_dict = self.get_state_dict()
        return self.state_dict2tensor(state_dict)

    def step(self, action):
        action = self.wrap_action(action)
        logger.debug('action:{}'.format(action))
        self.episode_counter += 1

        self.take_action(action)
        self.sleep(self.wait_action)

        state = self.get_state_dict()

        reward, done, info = self.calc_reward(state, action)
        state_array = self.state_dic_to_array(state)

        if self.episode_counter >= self.reset_step:
            done = True

        return state_array, reward, done, info

    def distance_to_target(self, observation):
        diff = 0
        for current_comp, target_comp in zip(self.components[c.CURRENT], self.components[c.TARGET]):
            for prop in self.components[c.PROPS]:
                if prop == 'velocity':  # ignore velocity in the reward
                    continue
                p_current = np.asarray(observation[current_comp[c.NAME]][prop])
                p_target = np.asarray(observation[target_comp[c.NAME]][prop])
                diff += np.linalg.norm(p_current - p_target)
        return diff

    def get_velocity(self, observation):
        diff = 0
        for current_comp, target_comp in zip(self.components[c.CURRENT], self.components[c.TARGET]):
            prop = 'velocity'
            p_current = np.asarray(observation[current_comp[c.NAME]][prop])
            p_target = np.asarray(observation[target_comp[c.NAME]][prop])
            diff += np.linalg.norm(p_current - p_target)
        return diff

    def non_sym_loss(self, excitations):
        """
        In the jaw model, right and left muscles are placed consecutively in the array,
        i.e.,  rat, lat, rmt, lmt, rpt, lpt, ... , rgh, lgh
        Here, a loss is defined by iterating over them and making sure the excitations of bilateral
        muscles are close to one another.
        :param excitations:
        :return:
        """
        return np.sum([np.abs(excitations[i] - excitations[i + 1])
                       for i in range(0, len(excitations), 2)])

    goal_th_step = 0
    def calc_reward(self, state, action):
        # initialize
        observation = state[c.OBSERVATION_STR]
        excitations = state[c.EXCITATIONS_STR]
        muscle_forces = state[c.MUSCLE_FORCES_STR]
        info = {}
        done = False
        done_reward = 0
        reward = 0

        info['time'] = state['time']
        info['rlProps'] = state['properties']

        phi_u = self.distance_to_target(observation)
        info['distance'] = phi_u
        reward -= self.w_u * np.log10(phi_u + c.EPSILON)

        # todo: the velocity might be removed... hard coded for now
        velocity = self.get_velocity(observation)

        if self.test_mode:
            # log all excitations and muscle forces
            info['excitations_each'] = excitations
            info['muscleForces_each'] = muscle_forces

            # log location of the mid-incisal point during test
            info['lowerIncisorPosition'] = observation['lowerincisor']['position']

        elif phi_u < self.goal_threshold:
            if not self.args.goal_hack:
                done = True
                done_reward = self.goal_reward
                logger.info(f'Done: {phi_u} < {self.goal_threshold}')
            else:
                self.goal_th_step += 1
                if self.goal_th_step >= 5:
                    done = True
                    done_reward = self.goal_reward
                    logger.info(f'Done: {phi_u} < {self.goal_threshold}, '
                                 f'goal_th_step={self.goal_th_step}')
                    self.goal_th_step = 0

        reward += done_reward

        info['muscleForces'] = np.linalg.norm(muscle_forces)
        phi_r = info['muscleForces']
        reward -= phi_r * self.w_r

        sym_loss = self.non_sym_loss(excitations)
        info['excitations'] = np.mean(excitations)
        info['symmetric_loss'] = sym_loss
        reward -= sym_loss * self.w_s

        logger.log(level=18, msg='reward={}  phi_u={}   phi_r={}'.format(reward, phi_u, phi_r))

        return reward, done, info

    def reset(self):
        self.episode_counter = 0
        # todo: remove this hack
        self.goal_th_step = 0
        return super().reset()


def write_infos(infos, path):
    import csv
    os.makedirs(path, exist_ok=True)

    # scalar values
    with open(os.path.join(path, 'scalar.csv'), 'w', newline='') as csvfile:
        fieldnames = []
        for key, value in infos.items():
            if isinstance(value, float):
                fieldnames.append(key)
        writer = csv.DictWriter(csvfile, fieldnames=fieldnames, extrasaction='ignore')
        writer.writeheader()
        writer.writerow(infos, )

    # list of lists
    fieldnames = []
    list_fields = []
    num_episodes = -1
    num_iters_per_episode = list()
    for key, episodes in infos.items():
        if isinstance(episodes, list):
            if isinstance(episodes[0][0], list):  # for each value of a muscle activation
                list_fields.append(key)
                for episode_num in range(len(episodes[0][0])):
                    fieldnames.append(key + str(episode_num))
            elif isinstance(episodes[0][0], dict):
                for k in episodes[0][0].keys():
                    fieldnames.append(k)
                    fieldnames.append('sum_' + k)
                    fieldnames.append('max_' + k)
            else:
                fieldnames.append(key)
                list_fields.append(key)
            if num_episodes != -1:
                assert num_episodes == len(episodes)  # make sure number of episodes is the same
            else:
                num_episodes = len(episodes)
                for episode in episodes:
                    num_iters_per_episode.append(len(episode))

    # print('list_fields', list_fields)
    # print('fieldnames', fieldnames)
    filenames = []
    for episode_num in range(num_episodes):
        filenames.append(os.path.join(path, f'episode_{episode_num}.csv'))
        with open(filenames[-1], 'w', newline='') as csvfile:
            writer = csv.DictWriter(csvfile, fieldnames=fieldnames)
            writer.writeheader()
            for iter_num in range(num_iters_per_episode[episode_num]):
                row = dict()
                for field in list_fields:
                    # print(type(infos[field][episode_num][iter_num]))
                    if isinstance(infos[field][episode_num][iter_num], list):
                        # print('list: ', field)
                        for k in range(len(infos[field][episode_num][iter_num])):
                            row[field + str(k)] = infos[field][episode_num][iter_num][k]
                    elif isinstance(infos[field][episode_num][iter_num], dict):
                        print('dict: ', field)
                        print(field)
                        for k, v in infos[field][episode_num][iter_num].items():
                            row['max_' + k] = max(v)
                            row['sum_' + k] = sum(v)
                            row[k] = v
                    else:
                        # print('scalar: ', field)
                        row[field] = infos[field][episode_num][iter_num]
                writer.writerow(row)

    # concatenate all episodes
    concatenated_filename = os.path.join(path, 'episode_all.csv')
    with open(concatenated_filename, 'w') as outfile:
        for filenumber, fname in enumerate(filenames):
            with open(fname) as infile:
                for lnumber, line in enumerate(infile):
                    if filenumber != 0 and lnumber == 0:
                        continue
                    outfile.write(line)


def maximum_occlusal_force(env, path):
    import time
    env.reset()
    print('Testing maximum occlusal force')

    # maximum closing muscles
    action_masseter = [-1, -1, -1, -1,
                       -1, -1, 1, 1,
                       1, 1, -1, -1,
                       -1, -1, -1, -1,
                       -1, -1, -1, -1,
                       -1, -1, -1, -1]

    action_zero = [-1, -1, -1, -1,
                   -1, -1, -1, -1,
                   -1, -1, -1, -1,
                   -1, -1, -1, -1,
                   -1, -1, -1, -1,
                   -1, -1, -1, -1]

    action = [1, 1, 1, 1,
              1, 1, 1, 1,
              1, 1, 1, 1,
              -1, -1, -1, -1,
              -1, -1, -1, -1,
              -1, -1, -1, -1]

    env.step(action_zero)
    time.sleep(1.0)

    env.step(action_masseter)
    time.sleep(2.0)

    env.step(action)
    time.sleep(3.0)

    next_state, reward, done, info = env.step(action)
    occlusalForces = info['rlProps']['occlusalForces']
    with open(os.path.join(path, 'occlusalForces.csv'), 'w', newline='') as file:
        for force in occlusalForces:
            file.write(str(force))
    print('sum of occlusal forces:', sum(occlusalForces))


def calculate_convex_hull(path):
    import csv
    concatenated_filename = os.path.join(path, 'episode_all.csv')
    points = []
    with open(concatenated_filename, 'r') as csvfile:
        reader = csv.DictReader(csvfile)
        for row in reader:
            points.append([row['all_lowerIncisorPosition0'],
                           row['all_lowerIncisorPosition1'],
                           row['all_lowerIncisorPosition2']])

    points = np.asarray(points)
    from scipy.spatial import ConvexHull
    hull = ConvexHull(points)
    print('Volume of ConvexHull:', hull.volume)
    with open(os.path.join(path, 'convexHull.txt'), 'w') as file:
        file.write(f'{hull.volume}')
