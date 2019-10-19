import gym
import torch

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
                        help='Temperature parameter α determines the relative importance of the entropy\
                                term against the reward (default: 0.2)')
    parser.add_argument('--automatic_entropy_tuning', type=str2bool, default=False, metavar='G',
                        help='Automaically adjust α (default: False)')
    parser.add_argument('--batch_size', type=int, default=256, metavar='N',
                        help='batch size (default: 256)')
    parser.add_argument('--num_steps', type=int, default=1000001, metavar='N',
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
    parser.add_argument('--cuda', action="store_true", default=True,
                        help='run on CUDA (default: True)')
    return parser


def main():
    args = extend_arguments(get_parser()).parse_args()
    configs = common.config.get_config(args)

    if args.test:
        args.num_processes = 1
        args.use_wandb = False

    logger = setup_logger(args.verbose, args.model_name, configs.log_directory)
    torch.set_num_threads(1)
    device = 'cuda:0' if torch.cuda.is_available() else 'cpu'

    # set seed values
    seed = args.seed
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)

    if args.use_wandb:
        import wandb
        resume_wandb = True if args.wandb_resume_id is not None else False
        wandb.init(config=args, resume=resume_wandb, id=args.wandb_resume_id, project='rl')

    env = gym.make(args.env, **vars(args))

    # Agent
    agent = SAC(env.observation_space.shape[0], env.action_space, args)
    if args.load_path:
        # todo: save and load the optimizer
        logger.info(f'loading model from {args.load_path}')
        agent.load_state_dict(torch.load(args.load_path))

    if args.test:
        test(env, agent, args)
    else:
        train(env, agent, args, configs)


if __name__ == "__main__":
    main()
