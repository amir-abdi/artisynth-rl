from datetime import datetime
from pathlib import Path

import tensorflow as tf
from keras import backend as K
from keras.backend.tensorflow_backend import set_session


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
    return 1 / (1 + K.exp(-0.1 * x))


begin_time = str(datetime.now().strftime('%y-%m-%d_%H-%M'))
config = tf.ConfigProto()
config.gpu_options.allow_growth = True
config.gpu_options.visible_device_list = "0"
set_session(tf.Session(config=config))


class Bunch(object):
    def __init__(self, adict):
        self.__dict__.update(adict)