import argparse


def str2bool(v):
    if v.lower() in ('yes', 'true', 't', 'y', '1'):
        return True
    elif v.lower() in ('no', 'false', 'f', 'n', '0'):
        return False
    else:
        raise argparse.ArgumentTypeError('Boolean value expected.')


def get_parser(parser=None):
    parser = parser or argparse.ArgumentParser(description='RL')
    parser.conflict_handler = 'resolve'

    # Environment instantiation
    parser.add_argument('--ip', type=str, default='localhost',
                        help='IP of server')
    parser.add_argument('--port', type=int, default=8080,
                        help='port to run the server on (default: 4545)')
    parser.add_argument('--env', default='Point2PointEnv-v0',
                        help='environment to train on (default: Point2PointEnv-v0)')
    parser.add_argument('--gui', type=str2bool, default=True,
                        help='run environment with GUI.')

    # Logging and saving
    parser.add_argument('--verbose', type=int, default='20',
                        help='Verbosity level')
    parser.add_argument('--project_name', default='ArtiSynth-RL',
                        help='Name of the RL project.')
    parser.add_argument('--experiment_name', default='UnknownModel',
                        help='Name of the experiment, for logging purposes.')
    parser.add_argument('--load_path', default=None,
                        help='Path to load the trained model.')
    parser.add_argument('--log_interval', type=int, default=10,
                        help='log interval, one log per n updates (default: 10)')
    parser.add_argument('--wait_action', type=float, default=0.0,
                        help='Wait (seconds) for action to take place and environment to stabilize.')
    parser.add_argument('--use_wandb', type=str2bool, default=False,
                        help='Use wandb for train logging.')
    parser.add_argument('--wandb_resume_id', default=None, type=str,
                        help='resume previous wandb run with id')
    parser.add_argument('--use_tensorboard', default=False,
                        help='use tensorboard for logging (default: True)')
    parser.add_argument('-logdir', type=str, default=None,
                        help='Directory to which results will be logged (default: ./)')
    parser.add_argument('--save_interval', type=int, default=10,
                        help='save interval, one save per n updates (default: 100)')
    parser.add_argument('--eval_interval', type=int, default=100,
                        help='eval interval, one eval per n updates (default: 100)')
    parser.add_argument('--episode_log_interval', type=int, default=1,
                        help='log interval for episodes (default: 10)')
    parser.add_argument('--eval_episode', type=int, default=5, help='Number of episodes to evaluate')

    # Model
    parser.add_argument('--incremental_actions', type=str2bool, default=False,
                        help='Treat actions as increment/decrements to the current excitations.')
    parser.add_argument('--reset_step', type=int, default=1e10, help='Reset envs every n iters.')
    parser.add_argument('--include_current_state', type=str2bool, default=True,
                        help='Include the current position/rotation of the model in the state.')
    parser.add_argument('--include_current_excitations', type=str2bool, default=True,
                        help='Include the current excitations of actuators in the state.')
    parser.add_argument('--goal_threshold', type=float, default=0.1,
                        help='Difference between real and target which is considered as success when reaching a goal')
    parser.add_argument('--goal_reward', type=float, default=0, help='The reward to give if goal was reached.')
    parser.add_argument('--zero_excitations_on_reset', type=str2bool, default=True,
                        help='Reset all muscle excitations to zero after each reset.')
    parser.add_argument('--lr', type=float, default=0.0003, metavar='G', help='learning rate (default: 0.0003)')
    parser.add_argument('--pow_u', type=float, default=1, help='weight of distance reward term')
    parser.add_argument('--w_u', type=float, default=1, help='weight of distance reward term')
    parser.add_argument('--w_d', type=float, default=1, help='weight of damping reward term')
    parser.add_argument('--w_r', type=float, default=1, help='weight of excitation regularization reward term')

    # others
    parser.add_argument('--test', type=str2bool, default=False, help='Evaluate a trained model.')
    parser.add_argument('--test_episode', type=int, default=10, help='Number of episodes to test')
    parser.add_argument('--alg', default='ppo',
                        help='algorithm to use: a2c | ppo | acktr | mpc')
    parser.add_argument('--seed', type=int, default=123456, metavar='N',
                        help='random seed (default: 123456)')

    return parser
