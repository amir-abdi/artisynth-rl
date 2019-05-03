import os
from pathlib import Path
from common.arguments import get_args

args = get_args()
env_name = args.env_name
model_name = args.model_name

root_path = str(Path.cwd() / 'results')
model_path = str(Path(root_path) / env_name / model_name)

trained_directory = str(Path(model_path) /  'trained')
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

