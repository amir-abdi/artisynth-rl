import gym
import torch
import pickle

from algs.sac.sac import SAC
import common.config
from common.arguments import get_parser
from common.config import setup_logger

from algs.sac.test_train import train, test
import artisynth_envs


def extend_arguments(parser):
    from common.arguments import str2bool
    parser.add_argument('--policy', default="Gaussian",
                        help='Policy Type: Gaussian | Deterministic (default: Gaussian)')
    parser.add_argument('--gamma', type=float, default=0.99, metavar='G',
                        help='discount factor for reward (default: 0.99)')
    parser.add_argument('--tau', type=float, default=0.005, metavar='G',
                        help='target smoothing coefficient(τ) (default: 0.005)')
    parser.add_argument('--alpha', type=float, default=0.2, metavar='G',
                        help='Temperature parameter α - the relative importance of the entropy '
                             'term (default: 0.2)')
    parser.add_argument('--automatic_entropy_tuning', type=str2bool, default=False, metavar='G',
                        help='Automaically adjust α (default: False)')
    parser.add_argument('--batch_size', type=int, default=256, metavar='N',
                        help='batch size (default: 256)')
    parser.add_argument('--num_steps', type=int, default=10000001, metavar='N',
                        help='maximum number of steps (default: 1000000)')
    parser.add_argument('--hidden_size', type=int, default=256, metavar='N',
                        help='hidden size (default: 256)')
    parser.add_argument('--updates_per_step', type=int, default=1, metavar='N',
                        help='model updates per simulator step (default: 1)')
    parser.add_argument('--start_steps', type=int, default=10000, metavar='N',
                        help='Steps sampling random actions (default: 10000)')
    parser.add_argument('--target_update_interval', type=int, default=1, metavar='N',
                        help='Value target update per no. of updates per step (default: 1)')
    parser.add_argument('--replay_size', type=int, default=1000000, metavar='N',
                        help='size of replay buffer (default: 1000000)')
    parser.add_argument('--cuda', type=str2bool, default=True,
                        help='run on CUDA (default: True)')

    # learning rate
    parser.add_argument('--lr_gamma', type=float, default=0.999995,
                        help='gamma value for the exponential learning rate scheduler')
    parser.add_argument('--lr_min', type=float, default=0,
                        help='gamma value for the exponential learning rate scheduler')

    # load model
    parser.add_argument('--memory_load_path', default=None,
                        help='Path to load the saved replay memory.')
    parser.add_argument('--reset_global_episode', type=str2bool, default=False,
                        help='start training episode from 0 or continue from the saved model.')
    parser.add_argument('--load_optim', type=str2bool, default=False,
                        help='to load the state of the optimizers, including the learning rate.')

    parser.add_argument('--goal_hack', type=str2bool, default=False)

    return parser


def main():
    args = extend_arguments(get_parser()).parse_args()
    configs = common.config.get_config(args.env, args.experiment_name)

    if args.test:
        args.num_processes = 1
        args.use_wandb = False

    logger = setup_logger(args.verbose, args.experiment_name, configs.log_directory)
    torch.set_num_threads(1)

    # set seed values
    seed = args.seed
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)

    if args.use_wandb:
        import wandb
        resume_wandb = True if args.wandb_resume_id is not None else False
        wandb.init(config=args, resume=resume_wandb, id=args.wandb_resume_id, project='rl',
                   name=args.experiment_name)

    env = gym.make(args.env, **vars(args))

    # Agent
    global_episodes = 0
    agent = SAC(env.observation_space.shape[0], env.action_space, args)
    if args.load_path:
        global_episodes = agent.load_model(args.load_path, args.load_optim) * int(
            not args.reset_global_episode)
        logger.info(f'Agent loaded: {args.load_path} @{global_episodes}')

    memory = None
    if args.memory_load_path:
        memory = pickle.load(open(args.memory_load_path, 'rb'))
        logger.info(f'Memory loaded: {args.memory_load_path}')
        logger.info(f'Loaded Memory Length: {len(memory)}')
        logger.warning('There is something wrong with loading experiments from memory and '
                       'the training becomes unstable. Be extra careful when using this feature!')

    if args.test:
        test(env, agent, args)
    else:
        train(env, agent, args, configs, memory, global_episodes)


if __name__ == "__main__":
    main()
