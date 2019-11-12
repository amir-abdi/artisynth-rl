import os
from pathlib import Path
import logging

from common.utilities import Bunch
import common.constants as c


def get_config(env_name, experiment_name):
    configs = dict()
    root_path = str(Path.cwd() / 'results')
    model_path = str(Path(root_path) / env_name / experiment_name)
    configs['root_path'] = root_path
    configs['model_path'] = model_path

    configs['trained_directory'] = str(Path(model_path) / 'trained')
    configs['tensorboard_log_directory'] = str(Path(model_path) / 'logs_tb')
    configs['env_log_directory'] = str(Path(model_path) / 'logs_env')
    configs['agent_log_directory'] = str(Path(model_path) / 'logs_agent')
    configs['log_directory'] = str(Path(model_path) / 'logs')
    configs['visdom_log_directory'] = str(Path(model_path) / 'logs_visdom')

    os.makedirs(configs['model_path'], exist_ok=True)
    os.makedirs(configs['trained_directory'], exist_ok=True)
    os.makedirs(configs['tensorboard_log_directory'], exist_ok=True)
    os.makedirs(configs['env_log_directory'], exist_ok=True)
    os.makedirs(configs['agent_log_directory'], exist_ok=True)
    os.makedirs(configs['log_directory'], exist_ok=True)
    os.makedirs(configs['visdom_log_directory'], exist_ok=True)

    return Bunch(configs)


class StyleFormatter(logging.Formatter):
    CSI = "\x1B["
    YELLOW = '33;40m'
    RED = '31;40m'

    # Add %(asctime)s after [ to include the time-date of the log
    high_style = '{}{}(%(levelname)s)[%(filename)s:%(lineno)d]  %(message)s{}0m'.format(CSI, RED, CSI)
    medium_style = '{}{}(%(levelname)s)[%(filename)s:%(lineno)d]  %(message)s{}0m'.format(CSI, YELLOW, CSI)
    low_style = '(%(levelname)s)[%(filename)s:%(lineno)d]  %(message)s'

    def __init__(self, fmt=None, datefmt='%b-%d %H:%M', style='%'):
        super().__init__(fmt, datefmt, style)

    def format(self, record):
        if record.levelno <= logging.INFO:
            self._style = logging.PercentStyle(StyleFormatter.low_style)
        elif record.levelno <= logging.WARNING:
            self._style = logging.PercentStyle(StyleFormatter.medium_style)
        else:
            self._style = logging.PercentStyle(StyleFormatter.high_style)

        return logging.Formatter.format(self, record)


def setup_logger(level=None, file_path=None, log_directory=None):
    if c.LOGGER_STR in logging.Logger.manager.loggerDict:
        logger = logging.getLogger(c.LOGGER_STR)
        return logging.getLogger(c.LOGGER_STR)
    from importlib import reload
    reload(logging)  # to turn off any changes to logging done by other imported libraries

    logger = logging.getLogger(c.LOGGER_STR)
    log_formatter = logging.Formatter("%(asctime)s [%(threadName)-12.12s] [%(levelname)-5.5s]  %(message)s")

    file_handler = logging.FileHandler("{0}/{1}.log".format(log_directory, file_path))
    file_handler.setFormatter(log_formatter)
    logger.addHandler(file_handler)

    console_handler = logging.StreamHandler()
    console_handler.setFormatter(log_formatter)
    console_handler.setFormatter(StyleFormatter())
    logger.addHandler(console_handler)

    logger.setLevel(level=level)
    logger.propagate = False  # to prohibit double logging to console
    logger.info('Log level: %i', level)

    return logger
