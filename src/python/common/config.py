import os
from pathlib import Path
import logging

from common.utilities import Bunch
import common.constants as c


def get_config(args):
    env_name = args.env
    model_name = args.model_name

    configs = dict()
    root_path = str(Path.cwd() / 'results')
    model_path = str(Path(root_path) / env_name / model_name)
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


def setup_logger(level, name, log_directory):
    if c.LOGGER_STR in logging.Logger.manager.loggerDict:
        return logging.getLogger(c.LOGGER_STR)
    logger = logging.getLogger(c.LOGGER_STR)
    log_formatter = logging.Formatter("%(asctime)s [%(threadName)-12.12s] [%(levelname)-5.5s]  %(message)s")

    file_handler = logging.FileHandler("{0}/{1}.log".format(log_directory, name))
    file_handler.setFormatter(log_formatter)
    logger.addHandler(file_handler)

    console_handler = logging.StreamHandler()
    console_handler.setFormatter(log_formatter)
    logger.addHandler(console_handler)

    logger.setLevel(level=level)
    logger.propagate = False  # to prohibit double logging to console
    logger.info('Log level: %i', level)
    return logger
