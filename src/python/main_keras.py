import os
import pprint
import numpy as np
import time

from rl.agents.dqn import NAFAgent
from rl.random import OrnsteinUhlenbeckProcess
from rl.callbacks import RlTensorBoard
from rl.memory import SequentialMemory

import keras
from keras.utils.generic_utils import get_custom_objects
from keras.models import Sequential, Model
from keras.layers import Dense, Activation, Flatten, Input, Concatenate
from keras.optimizers import Adam

from python.artisynth_envs.envs.point_model2d_env import PointModel2dEnv, PointModel2dProcessor
from python.common import config as c

os.environ["CUDA_DEVICE_ORDER"] = "PCI_BUS_ID"  # see issue #152
os.environ["CUDA_VISIBLE_DEVICES"] = ""

# logging parameters
VERBOSITY = 4
HISTOGRAM_FREQ = 1

# Port number
PORT = 7024

# Constants of the environment
NUM_MUSCLES = 6
SUCCESS_THRESHOLD = 0.5
DOF_OBSERVATIONS = 3

# Noise parameters
THETA = .35
MU = 0.
SIGMA = .35
DT = 1e-1
SIGMA_MIN = 0.05
NUM_STEPS_ANNEALING = 300000

# Training hyper-parameters
GAMMA = 0.99
LR = 1e-2
NUM_MAX_EPISODE_STEPS = 200
NUM_TRAINING_STEPS = 5000000
BATCH_SIZE = 32
UPDATE_TARGET_MODEL_STEPS = 200
WARMUP_STEPS = 200
MEMORY_SIZE = 50000

# Testing parameters
NUM_EPISODES = 500


def smooth_logistic(x):
    return 1 / (1 + K.exp(-0.1 * x))


def get_v_model(env):
    v_model = Sequential()
    v_model.add(Flatten(input_shape=(1,) + env.observation_space.shape,
                        name='FirstFlatten'))
    v_model.add(Dense(32))
    v_model.add(Activation('relu'))
    v_model.add(Dense(32))
    v_model.add(Activation('relu'))
    v_model.add(Dense(1))
    v_model.add(Activation('relu', name='V_final'))
    print(v_model.summary())
    return v_model


def get_mu_model(env):
    mu_model = Sequential()
    mu_model.add(Flatten(input_shape=(1,) + env.observation_space.shape,
                         name='FirstFlatten'))
    mu_model.add(Dense(32))
    mu_model.add(Activation('relu'))
    mu_model.add(Dense(32))
    mu_model.add(Activation('relu'))
    mu_model.add(Dense(env.action_space.shape[0]))
    mu_model.add(Activation('SmoothLogistic', name='mu_final'))
    print(mu_model.summary())
    return mu_model


def get_l_model(env):
    nb_actions = env.action_space.shape[0]
    action_input = Input(shape=(nb_actions,), name='action_input')
    observation_input = Input(shape=(1,) + env.observation_space.shape,
                              name='observation_input')
    x = Concatenate()([action_input, Flatten()(observation_input)])
    x = Dense(32)(x)
    x = Activation('relu')(x)
    x = Dense(32)(x)
    x = Activation('relu')(x)
    x = Dense(32)(x)
    x = Activation('relu')(x)
    x = Dense(((nb_actions * nb_actions + nb_actions) // 2))(x)
    x = Activation('linear', name='L_final')(x)
    l_model = Model(inputs=[action_input, observation_input], outputs=x)
    print(l_model.summary())
    return l_model


class MuscleNAFAgent(NAFAgent):
    def select_action(self, state):
        batch = self.process_state_batch([state])
        action = self.mu_model.predict_on_batch(batch).flatten()
        assert action.shape == (self.nb_actions,)

        # Apply noise, if a random process is set.
        if self.training and self.random_process is not None:
            noise = self.random_process.sample()
            assert noise.shape == action.shape
            action += noise
            # This is necessary even if using logistic or sigmoid activations
            # because of the added noise to avoid negative and above 1 values
            # for excitations.
            action = np.clip(action, 0, 1)
        return action


def main(train_test_flag='train'):
    get_custom_objects().update(
        {'SmoothLogistic': Activation(smooth_logistic)})
    model_name = '2,2,3x32Net_r4_lr{}_th{}_[t{}s{}]_nAnn[{},{}]_{}'. \
        format(LR,
               SUCCESS_THRESHOLD,
               THETA,
               SIGMA,
               SIGMA_MIN,
               NUM_STEPS_ANNEALING,
               NUM_MUSCLES)
    muscle_labels = ["m" + str(i) for i in np.array(range(NUM_MUSCLES))]

    training = False
    weight_filename = os.path.join(c.trained_directory, '{}_weights.h5f'.format(model_name))
    log_file_name = begin_time + '_' + model_name

    while True:
        try:
            env = PointModel2dEnv(verbose=0, success_thres=SUCCESS_THRESHOLD,
                                  dof_observation=DOF_OBSERVATIONS,
                                  include_follow=False, port=PORT,
                                  muscle_labels=muscle_labels,
                                  log_file=log_file_name)
            break
        except ConnectionRefusedError as e:
            print("Server not started: ", e)
            time.sleep(10)
    try:
        env.seed(123)
        nb_actions = env.action_space.shape[0]
        memory = SequentialMemory(limit=MEMORY_SIZE, window_length=1)

        mu_model = get_mu_model(env)
        v_model = get_v_model(env)
        l_model = get_l_model(env)

        random_process = OrnsteinUhlenbeckProcess(
            size=nb_actions,
            theta=THETA,
            mu=MU,
            sigma=SIGMA,
            dt=DT,
            sigma_min=SIGMA_MIN,
            n_steps_annealing=NUM_STEPS_ANNEALING
        )
        # random_process = None
        processor = PointModel2dProcessor()
        agent = MuscleNAFAgent(nb_actions=nb_actions, V_model=v_model,
                               L_model=l_model, mu_model=mu_model,
                               memory=memory,
                               nb_steps_warmup=WARMUP_STEPS,
                               random_process=random_process,
                               gamma=GAMMA,
                               target_model_update=UPDATE_TARGET_MODEL_STEPS,
                               processor=processor,
                               target_episode_update=True)

        agent.compile(Adam(lr=LR), metrics=['mse'])
        env.agent = agent
        pprint.pprint(agent.get_config(False))
        load_weights(agent, weight_filename)

        tensorboard = RlTensorBoard(
            log_dir=os.path.join(c.tensorboard_log_directory, log_file_name),
            histogram_freq=HISTOGRAM_FREQ,
            batch_size=BATCH_SIZE,
            write_graph=True,
            write_grads=True, write_images=False, embeddings_freq=0,
            embeddings_layer_names=None, embeddings_metadata=None,
            agent=agent)
        csv_logger = keras.callbacks.CSVLogger(
            os.path.join(c.agent_log_directory, log_file_name),
            append=False, separator=',')

        if train_test_flag == 'train':
            # train code
            training = True
            agent.fit(env,
                      nb_steps=NUM_TRAINING_STEPS,
                      visualize=False,
                      verbose=VERBOSITY,
                      nb_max_episode_steps=NUM_MAX_EPISODE_STEPS,
                      callbacks=[tensorboard, csv_logger])
            print('Training complete')
            save_weights(agent, weight_filename)
        elif train_test_flag == 'test':
            # test code
            training = False
            env.log_to_file = False
            history = agent.test(env, nb_episodes=NUM_EPISODES,
                                 nb_max_episode_steps=NUM_MAX_EPISODE_STEPS)
            print(history.history)
            print('Average last distance: ',
                  np.mean(history.history['last_distance']))
            print('Mean Reward: ', np.mean(history.history['episode_reward']))

    except Exception as e:
        if training:
            save_weights(agent, weight_filename)
        print("Error in main code:", str(e))
        env.net.sock.close()
        raise e


if __name__ == "__main__":
    main('train')
