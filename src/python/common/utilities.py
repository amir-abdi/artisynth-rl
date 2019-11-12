from datetime import datetime
from pathlib import Path
from torch.optim.lr_scheduler import _LRScheduler

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


def get_env_type(args):
    return 'artisynth', args.env


def get_lr_pytorch(optimizer):
    for param_group in optimizer.param_groups:
        return param_group['lr']


class ExponentialLRWithMin(_LRScheduler):
    """Set the learning rate of each parameter group to the initial lr decayed
    by gamma every epoch. When last_epoch=-1, sets initial lr as lr.
    Learning rate is always >= min

    Args:
        optimizer (Optimizer): Wrapped optimizer.
        gamma (float): Multiplicative factor of learning rate decay.
        last_epoch (int): The index of last epoch. Default: -1.
    """

    def __init__(self, optimizer, gamma, last_epoch=-1, min=0):
        self.gamma = gamma
        self.min = min
        super(ExponentialLRWithMin, self).__init__(optimizer, last_epoch)

    def get_lr(self):
        return [max(base_lr * self.gamma ** self.last_epoch, self.min)
                for base_lr in self.base_lrs]
