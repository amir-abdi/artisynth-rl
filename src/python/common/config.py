import os
from pathlib import Path


# from common.arguments import get_args
# from baselines.common.cmd_util import common_arg_parser, parse_unknown_args
# import sys

# args = sys.argv   #get_args()
# arg_parser = common_arg_parser()
# args, unknown_args = arg_parser.parse_known_args(args)
# print('arguments are:', args)
# print('model name is', args.model)


def set_config(args):
    env_name = args.env  # args.env_name
    model_name = args.model

    root_path = str(Path.cwd() / 'results')
    model_path = str(Path(root_path) / env_name / model_name)

    trained_directory = str(Path(model_path) / 'trained')
    tensorboard_log_directory = str(Path(model_path) / 'logs_tb')
    env_log_directory = str(Path(model_path) / 'logs_env')
    agent_log_directory = str(Path(model_path) / 'logs_agent')
    log_directory = str(Path(model_path) / 'logs')
    visdom_log_directory = str(Path(model_path) / 'logs_visdom')

    os.makedirs(model_path, exist_ok=True)
    os.makedirs(trained_directory, exist_ok=True)
    os.makedirs(tensorboard_log_directory, exist_ok=True)
    os.makedirs(env_log_directory, exist_ok=True)
    os.makedirs(agent_log_directory, exist_ok=True)
    os.makedirs(log_directory, exist_ok=True)
    os.makedirs(visdom_log_directory, exist_ok=True)
