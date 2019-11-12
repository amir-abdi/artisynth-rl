import os
import pprint
import numpy as np
import gym

from rl.agents.dqn import NAFAgent
from rl.random import OrnsteinUhlenbeckProcess
from rl.memory import SequentialMemory

import keras
from keras import backend as K
from keras.utils.generic_utils import get_custom_objects
from keras.models import Sequential, Model
from keras.layers import Dense, Activation, Flatten, Input, Concatenate
from keras.optimizers import Adam

from common.arguments import get_parser
from common.utilities import setup_tensorflow
import common.config
from common.config import setup_logger

# Noise parameters
THETA = .35
MU = 0.
SIGMA = .35
DT = 1e-1
SIGMA_MIN = 0.05
NUM_STEPS_ANNEALING = 300000

# Training hyper-parameters
GAMMA = 0.99
NUM_TRAINING_STEPS = 5000000
BATCH_SIZE = 32
UPDATE_TARGET_MODEL_STEPS = 200
WARMUP_STEPS = 200
MEMORY_SIZE = 50000


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

            # Clipping is necessary to avoid negative and above 1 values for excitations.
            action = np.clip(action, 0, 1)
        return action


def extend_arguments(parser):
    from common.arguments import str2bool
    parser.add_argument('--use_csvlogger', type=str2bool, default=False,
                        help='Use csvlogger to log training.')
    return parser


def main():
    args = extend_arguments(get_parser()).parse_args()
    configs = common.config.get_config(args.env, args.experiment_name)
    setup_tensorflow()
    get_custom_objects().update({'SmoothLogistic': Activation(smooth_logistic)})

    save_path = os.path.join(configs.trained_directory,
                             args.alg + "-" + args.env + ".h5f")

    log_file_name = args.model_name
    logger = setup_logger(args.verbose, log_file_name, configs.log_directory)

    import artisynth_envs.envs  # imported here to avoid the conflict with tensorflow's logger
    env = gym.make(args.env, **vars(args))
    env.seed(args.seed)

    try:
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

        agent = MuscleNAFAgent(nb_actions=nb_actions, V_model=v_model,
                               L_model=l_model, mu_model=mu_model,
                               memory=memory,
                               nb_steps_warmup=WARMUP_STEPS,
                               random_process=random_process,
                               gamma=GAMMA,
                               target_model_update=UPDATE_TARGET_MODEL_STEPS)

        agent.compile(Adam(lr=args.lr), metrics=['mse'])
        env.agent = agent

        if args.load_path is not None:
            agent.load_weights(args.load_path)
            logger.info(f'Wights loaded from: {args.load_path}')

        callbacks = []
        if args.use_tensorboard:
            from rl.callbacks import RlTensorBoard
            tensorboard = RlTensorBoard(
                log_dir=os.path.join(configs.tensorboard_log_directory, log_file_name),
                histogram_freq=1,
                batch_size=BATCH_SIZE,
                write_graph=True,
                write_grads=True, write_images=False, embeddings_freq=0,
                embeddings_layer_names=None, embeddings_metadata=None,
                agent=agent)
            callbacks.append(tensorboard)
        if args.use_csvlogger:
            csv_logger = keras.callbacks.CSVLogger(
                os.path.join(configs.agent_log_directory, log_file_name),
                append=False, separator=',')
            callbacks.append(csv_logger)

        if not args.test:  # train code
            training = True
            agent.fit(env,
                      nb_steps=NUM_TRAINING_STEPS,
                      visualize=False,
                      verbose=args.verbose,
                      nb_max_episode_steps=args.reset_step,
                      callbacks=callbacks)
            logger.info('Training complete')
            agent.save_weights(save_path)
        else:  # test code
            logger.info("Testing")
            training = False
            env.log_to_file = False
            history = agent.test(env, nb_episodes=args.test_episode, nb_max_episode_steps=args.reset_step)
            logger.info(history.history)
            logger.info('Average last distance: ',
                        np.mean(history.history['last_distance']))
            logger.info('Mean Reward: ', np.mean(history.history['episode_reward']))

    except Exception as e:
        if training:
            agent.save_weights(save_path)
        logger.info("Error in main code:", str(e))
        raise e


if __name__ == "__main__":
    main()
