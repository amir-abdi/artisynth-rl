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

    parser.add_argument('--w_u', type=float, default=1)
    parser.add_argument('--w_d', type=float, default=0.00005)
    parser.add_argument('--w_r', type=float, default=0.05)

    parser.add_argument('--ip', type=str, default='localhost',
                        help='IP of server')
    parser.add_argument('--verbose', type=int, default='20',
                        help='Verbosity level')
    # todo: remove the following try except
    try:
        parser.add_argument('--env', default='Point2PointEnv-v0',
                            help='environment to train on (default: Point2PointEnv-v0)')
    except argparse.ArgumentError:
        pass

    parser.add_argument('--model-name', default='testModel',
                        help='Name of the RL model being trained for logging purposes.')
    parser.add_argument('--load-path', default=None,
                        help='Path to load the trained model.')
    parser.add_argument('--port', type=int, default=8080,
                        help='port to run the server on (default: 4545)')
    parser.add_argument('--visdom-port', type=int, default=8097,
                        help='port to run the server on (default: 8097)')
    parser.add_argument('--episode-log-interval', type=int, default=1,
                        help='log interval for episodes (default: 10)')
    parser.add_argument('--log-interval', type=int, default=10,
                        help='log interval, one log per n updates (default: 10)')
    parser.add_argument('--wait-action', type=float, default=0.0,
                        help='Wait (seconds) for action to take place and environment to stabilize.')
    parser.add_argument('--episodic', type=str2bool, default=False,
                        help='Whether task is episodic.')
    parser.add_argument('--test', type=str2bool,  default=False,
                        help='Only evaluate a trained model.')
    parser.add_argument('--incremental_actions', type=str2bool, default=False,
                        help='Treat actions as increment/decrements to the current excitations.')
    parser.add_argument('--use-wandb', type=str2bool,  default=False,
                        help='Use wandb for train logging.')
    parser.add_argument('--wandb_resume_id', default=None, type=str,
                        help='resume previous wandb run with id')
    parser.add_argument('--reset-step', type=int, default=1e10,
                        help='Reset envs every n iters.')
    parser.add_argument('--hidden-layer-size', type=int, default=64,
                        help='Number of neurons in all hidden layers.')
    parser.add_argument('--algo', default='ppo',
                        help='algorithm to use: a2c | ppo | acktr | mpc')
    parser.add_argument('--lr', type=float, default=7e-4,
                        help='learning rate (default: 7e-4)')
    parser.add_argument('--eps', type=float, default=1e-5,
                        help='RMSprop optimizer epsilon (default: 1e-5)')
    parser.add_argument('--alpha', type=float, default=0.99,
                        help='RMSprop optimizer apha (default: 0.99)')
    parser.add_argument('--gamma', type=float, default=0.99,
                        help='discount factor for rewards (default: 0.99)')
    parser.add_argument('--use-gae', type=str2bool,  default=False,
                        help='use generalized advantage estimation')
    parser.add_argument('--tau', type=float, default=0.95,
                        help='gae parameter (default: 0.95)')
    parser.add_argument('--entropy-coef', type=float, default=0.01,
                        help='entropy term coefficient (default: 0.01)')
    parser.add_argument('--value-loss-coef', type=float, default=0.5,
                        help='value loss coefficient (default: 0.5)')
    parser.add_argument('--max-grad-norm', type=float, default=0.5,
                        help='max norm of gradients (default: 0.5)')
    # todo: fix the following conflict
    try:
        parser.add_argument('--seed', type=int, default=1,
                            help='random seed (default: 1)')
    except argparse.ArgumentError:
        pass
    parser.add_argument('--num-processes', type=int, default=1,
                        help='how many training CPU processes to use (default: 16)')
    parser.add_argument('--num-steps', type=int, default=5,
                        help='number of forward steps (default: 5)')
    parser.add_argument('--num-steps-eval', type=int, default=5,
                        help='number of forward steps in evaluation (default: 5)')
    parser.add_argument('--ppo-epoch', type=int, default=4,
                        help='number of ppo epochs (default: 4)')
    parser.add_argument('--num-mini-batch', type=int, default=32,
                        help='number of batches for ppo (default: 32)')
    parser.add_argument('--clip-param', type=float, default=0.2,
                        help='ppo clip parameter (default: 0.2)')
    parser.add_argument('--save-interval', type=int, default=100,
                        help='save interval, one save per n updates (default: 100)')
    parser.add_argument('--eval-interval', type=int, default=100,
                        help='eval interval, one eval per n updates (default: 100)')
    parser.add_argument('--vis-interval', type=int, default=100,
                        help='vis interval, one log per n updates (default: 100)')
    parser.add_argument('--num-env-steps', type=int, default=10e6,
                        help='number of environment steps to train (default: 10e6)')
    parser.add_argument('--no-cuda', type=str2bool,  default=False,
                        help='disables CUDA training')
    parser.add_argument('--add-timestep', type=str2bool,  default=False,
                        help='add timestep to observations')
    parser.add_argument('--recurrent-policy', type=str2bool,  default=False,
                        help='use a recurrent policy')
    parser.add_argument('--use-linear-lr-decay', type=str2bool,  default=False,
                        help='use a linear schedule on the learning rate')
    parser.add_argument('--use-linear-clip-decay', type=str2bool,  default=False,
                        help='use a linear schedule on the ppo clipping parameter')
    parser.add_argument('--vis', type=str2bool,  default=False,
                        help='enable visdom visualization')
    parser.add_argument('--eval-mode', type=str2bool,  default=False,
                        help='Initialize environment in evalulation mode.')
    parser.add_argument('--use-tensorboard', type=str2bool, default=False,
                        help='Use tensorboard to log training.')
    parser.add_argument('--use-csvlogger', type=str2bool, default=False,
                        help='Use csvlogger to log training.')
    parser.add_argument('--include-current-pos', type=str2bool,  default=False,
                        help='Include the current position/rotation of the model in defining the state.')
    parser.add_argument('--goal-threshold', type=float, default=0.1,
                        help='Difference between real and target which is considered as success when reaching a goal')
    parser.add_argument('--goal-terminal', type=str2bool,  default=True,
                        help='To stop the episode if target goal was reached.')
    parser.add_argument('--goal-reward', type=float, default=1,
                        help='The reward to give if goal was reached.')

    # PETS MPC args
    parser.add_argument('-ca', '--ctrl_arg', action='append', nargs=2, default=[],
                        help='Controller arguments, see https://github.com/kchua/handful-of-trials#controller-arguments')
    parser.add_argument('-o', '--override', action='append', nargs=2, default=[],
                        help='Override default parameters, see https://github.com/kchua/handful-of-trials#overrides')
    parser.add_argument('-logdir', type=str, default='log',
                        help='Directory to which results will be logged (default: ./results)')

    # initialize artisynth
    parser.add_argument('--init-artisynth', type=str2bool, default=False,
                        help='Initialize ArtiSynth automatically.')
    parser.add_argument('--artisynth-model', default='RlPoint2PointModel',
                        help='Name of the artisynth model to run. The model is expected to be inside the '
                             'package artisynth.models.rl')
    parser.add_argument('--artisynth-args', default='',
                        help='Arguments used in artisynth model initialization.')


    # args = parser.parse_args()
    # args.cuda = not args.no_cuda and torch.cuda.is_available()

    return parser

