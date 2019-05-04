from datetime import datetime
from pathlib import Path
import logging

import common.config as config

start_time = str(datetime.now().strftime('%y-%m-%d_%H-%M'))


def load_weights(agent, weight_filename):
    import os
    filename_temp, extension = os.path.splitext(weight_filename)
    if Path.exists(Path(filename_temp + '.h5f')) or \
            Path.exists(Path(filename_temp + '_actor.h5f')):
        agent.load_weights(str(weight_filename))
        print('weights loaded from ', str(weight_filename))


def save_weights(agent, weight_filename, ask=True):
    if ask:
        print('Save weights? (Y|N)')
        answer = input()
        if answer.lower() != 'n':
            agent.save_weights(weight_filename, overwrite=True)
            print('results saved to ', weight_filename)
        else:
            print('weights not saved')
    else:
        agent.save_weights(weight_filename, overwrite=True)
        print('results saved to ', weight_filename)


def mylogistic(x):
    from keras import backend as K
    return 1 / (1 + K.exp(-0.1 * x))


def setup_tensorflow():
    import tensorflow as tf
    from keras.backend.tensorflow_backend import set_session
    tf_config = tf.ConfigProto()
    tf_config.gpu_options.allow_growth = True
    tf_config.gpu_options.visible_device_list = "0"
    set_session(tf.Session(config=tf_config))


class Bunch(object):
    def __init__(self, adict):
        self.__dict__.update(adict)


def setup_logger(logger, level, name):
    log_formatter = logging.Formatter("%(asctime)s [%(threadName)-12.12s] [%(levelname)-5.5s]  %(message)s")

    file_handler = logging.FileHandler("{0}/{1}.log".format(config.log_directory, name))
    file_handler.setFormatter(log_formatter)
    logger.addHandler(file_handler)

    console_handler = logging.StreamHandler()
    console_handler.setFormatter(log_formatter)
    logger.addHandler(console_handler)

    logger.setLevel(level=level)
    logger.info('Log level: %i', level)

