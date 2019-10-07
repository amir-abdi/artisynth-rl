import os
import time
from collections import deque
import numpy as np
import torch

from a2c_ppo_acktr import algo
from a2c_ppo_acktr.model import Policy
from a2c_ppo_acktr.storage import RolloutStorage
from a2c_ppo_acktr.utils import update_linear_schedule

from artisynth_envs.make_env_pytorch import make_vec_envs_pytorch, wrap_env_pytorch
import common.config
from common.arguments import get_parser
from common.config import setup_logger

device = 'cuda:0' if torch.cuda.is_available() else 'cpu'


def extend_arguments(parser):
    from common.arguments import str2bool
    parser.add_argument('--ppo_epoch', type=int, default=4,
                        help='number of ppo epochs (default: 4)')
    parser.add_argument('--clip_param', type=float, default=0.2,
                        help='clip parameter (default: 0.2)')
    parser.add_argument('--episodic', type=str2bool, default=True,
                        help='Whether task is episodic.')
    parser.add_argument('--allow_early_resets', type=str2bool, default=True,
                        help='To allow resetting the environment before done.')
    parser.add_argument('--hidden_layer_size', type=int, default=64,
                        help='Number of neurons in all hidden layers.')
    parser.add_argument('--use_linear_lr_decay', type=str2bool, default=False,
                        help='use a linear schedule on the learning rate')
    parser.add_argument('--use_linear_clip_decay', type=str2bool, default=False,
                        help='use a linear schedule on the ppo clipping parameter')
    parser.add_argument('--add_timestep', type=str2bool, default=False,
                        help='add timestep to observations')
    parser.add_argument('--recurrent_policy', type=str2bool, default=False,
                        help='use a recurrent policy')
    parser.add_argument('--eps', type=float, default=1e-5,
                        help='RMSprop optimizer epsilon (default: 1e-5)')
    parser.add_argument('--use_gae', type=str2bool, default=False,
                        help='use generalized advantage estimation')
    parser.add_argument('--entropy_coef', type=float, default=0.01,
                        help='entropy term coefficient (default: 0.01)')
    parser.add_argument('--value_loss_coef', type=float, default=0.5,
                        help='value loss coefficient (default: 0.5)')
    parser.add_argument('--max_grad_norm', type=float, default=0.5,
                        help='max norm of gradients (default: 0.5)')
    parser.add_argument('--num_processes', type=int, default=1,
                        help='how many training CPU processes to use (default: 16)')
    parser.add_argument('--num_steps', type=int, default=5,
                        help='number of forward steps (default: 5)')
    parser.add_argument('--num_steps_eval', type=int, default=5,
                        help='number of forward steps in evaluation (default: 5)')
    parser.add_argument('--num_mini_batch', type=int, default=32,
                        help='number of batches for ppo (default: 32)')
    parser.add_argument('--num_env_steps', type=int, default=10e6,
                        help='number of environment steps to train (default: 10e6)')
    parser.add_argument('--gamma', type=float, default=0.99, metavar='G',
                        help='discount factor for reward (default: 0.99)')
    parser.add_argument('--tau', type=float, default=0.005, metavar='G',
                        help='target smoothing coefficient(τ) (default: 0.005)')
    return parser


def main():
    args = extend_arguments(get_parser()).parse_args()
    configs = common.config.get_config(args)
    assert args.alg in ['a2c', 'ppo', 'acktr', 'sac']
    if args.recurrent_policy:
        assert args.alg in ['a2c', 'ppo'], 'Recurrent policy is not implemented for ACKTR'

    if args.test:
        args.num_processes = 1
        args.use_wandb = False

    logger = setup_logger(args.verbose, args.model_name, configs.log_directory)
    torch.set_num_threads(1)

    # set seed values
    seed = args.seed
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)

    if args.use_wandb:
        import wandb
        resume_wandb = True if args.wandb_resume_id is not None else False
        wandb.init(config=args, resume=resume_wandb, id=args.wandb_resume_id, project='rl')

    # make environements (envs[0] is used for evaluation)
    envs, env_vector = make_vec_envs_pytorch(args.env, return_evn_vector=True,
                                             device=device, log_dir=configs.log_directory, **vars(args))
    eval_envs = wrap_env_pytorch(env_vector[0], args.gamma, device)

    actor_critic = Policy(envs.observation_space.shape, envs.action_space,
                          base_kwargs={'recurrent': args.recurrent_policy,
                                       'hidden_size': args.hidden_layer_size})
    # load model
    if args.load_path is not None:
        logger.info("loading model: {}".format(args.load_path))
        actor_critic = torch.load(args.load_path)

    actor_critic.to(device)

    if args.test:
        test(eval_envs, actor_critic, args, logger)
    else:
        train(envs, env_vector, eval_envs, actor_critic, args, configs, logger)


def train(envs, env_vector, eval_envs, actor_critic, args, configs, logger):
    episode_rewards = deque(maxlen=20)
    episode_distance = deque(maxlen=20)
    episode_phi_r = deque(maxlen=20)

    if args.alg == 'a2c':
        agent = algo.A2C_ACKTR(actor_critic, args.value_loss_coef,
                               args.entropy_coef, lr=args.lr,
                               eps=args.eps, alpha=args.alpha,
                               max_grad_norm=args.max_grad_norm)
    elif args.alg == 'ppo':
        agent = algo.PPO(actor_critic, args.clip_param, args.ppo_epoch, args.num_mini_batch,
                         args.value_loss_coef, args.entropy_coef, lr=args.lr,
                         eps=args.eps,
                         max_grad_norm=args.max_grad_norm,
                         use_clipped_value_loss=True)
    elif args.alg == 'acktr':
        agent = algo.A2C_ACKTR(actor_critic, args.value_loss_coef,
                               args.entropy_coef, acktr=True)
    else:
        raise NotImplementedError

    rollouts = RolloutStorage(args.num_steps, args.num_processes,
                              envs.observation_space.shape, envs.action_space,
                              actor_critic.recurrent_hidden_state_size)

    obs = envs.reset()
    rollouts.obs[0].copy_(obs)
    rollouts.to(device)

    # --------------------- train ----------------------------
    num_updates = int(args.num_env_steps) // args.num_steps // args.num_processes
    start = time.time()
    for epoch in range(num_updates):
        logger.info('Training {}/{} updates'.format(epoch, num_updates))
        envs.reset()

        # decrease learning rate linearly
        if args.use_linear_lr_decay:
            if args.alg == "acktr":
                # use optimizer's learning rate since it's hard-coded in kfac.py
                lr = update_linear_schedule(agent.optimizer, epoch, num_updates, agent.optimizer.lr)
            else:
                lr = update_linear_schedule(agent.optimizer, epoch, num_updates, args.lr)
        else:
            lr = args.lr

        if args.alg == 'ppo' and args.use_linear_clip_decay:
            agent.clip_param = args.clip_param * (1 - epoch / float(num_updates))

        distances = []
        vels = []

        for step in range(args.num_steps):
            # Sample actions
            with torch.no_grad():
                value, action, action_log_prob, recurrent_hidden_states = actor_critic.act(
                    rollouts.obs[step],
                    rollouts.recurrent_hidden_states[step],
                    rollouts.masks[step])

            # Observe reward and next obs
            obs, reward, done, infos = envs.step(action)

            for info in infos:
                if 'episode' in info.keys():
                    episode_rewards.append(info['episode']['r'])
                    episode_distance.append(info['episode_']['distance'])
                    episode_phi_r.append(info['episode_']['phi_r'])
                if 'distance' in info.keys():
                    distances.append(info['distance'])
                    vels.append(info['vel'])

            # If done then clean the history of observations.
            masks = torch.FloatTensor([[0.0] if done_ else [1.0] for done_ in done])
            # Masks that indicate whether it's a true terminal state or time limit end state
            bad_masks = torch.FloatTensor([[0.0] if 'bad_transition' in info.keys() else [1.0]
                                           for info in infos])

            rollouts.insert(obs, recurrent_hidden_states, action, action_log_prob, value, reward, masks, bad_masks)

        with torch.no_grad():
            next_value = actor_critic.get_value(rollouts.obs[-1],
                                                rollouts.recurrent_hidden_states[-1],
                                                rollouts.masks[-1]).detach()

        rollouts.compute_returns(next_value, args.use_gae, args.gamma, args.tau)
        value_loss, action_loss, dist_entropy = agent.update(rollouts)
        rollouts.after_update()

        # save for every interval-th episode or for the last epoch
        if epoch % args.save_interval == 0 or epoch == num_updates - 1:
            save_path = os.path.join(configs.trained_directory,
                                     args.alg + "-" + args.env + ".pt")
            logger.info("Saving model: {}".format(save_path))
            torch.save(actor_critic, save_path)

        total_num_steps = (epoch + 1) * args.num_processes * args.num_steps

        log_info = {
            'average_vel': np.mean(vels),
            'average_distance': np.mean(distances),
            'value_loss': value_loss,
            'action_loss': action_loss,
            'dist_entropy': dist_entropy,
            'lr': lr,
            'agent_clip_param': agent.clip_param,
        }

        if len(episode_rewards) > 1:
            log_info.update({'mean_episode_reward': np.mean(episode_rewards),
                             # 'median_episode_reward': np.median(episode_rewards),
                             # 'min_episode_reward': np.min(episode_rewards),
                             # 'max_episode_reward': np.max(episode_rewards),
                             'mean_episode_distance': np.mean(episode_distance),
                             'mean_episode_phi_r': np.mean(episode_phi_r)
                             })

        # todo: switch to episodic and cover other locations. This log is only for episodic
        if epoch % args.episode_log_interval == 0 and len(episode_rewards) > 1:
            end = time.time()
            logger.info(
                "Updates {}, num timesteps {}, FPS {} - Last {} training episodes: mean/median "
                "reward {:.1f}/{:.1f}, min/max reward {:.1f}/{:.1f}".format(epoch, total_num_steps,
                                                                            int(total_num_steps / (end - start)),
                                                                            len(episode_rewards),
                                                                            np.mean(episode_rewards),
                                                                            np.median(episode_rewards),
                                                                            np.min(episode_rewards),
                                                                            np.max(episode_rewards), dist_entropy,
                                                                            value_loss, action_loss))

        # --------------------- evaluate ----------------------------
        # Evaluate on a single environment
        env_vector[0].test_mode = False

        if args.eval_interval is not None and epoch % args.eval_interval == 0:
            logger.info('Evaluate')

            eval_episode_rewards = []
            rewards = []
            obs = eval_envs.reset()
            eval_recurrent_hidden_states = torch.zeros(args.num_processes,
                                                       actor_critic.recurrent_hidden_state_size, device=device)
            eval_masks = torch.zeros(args.num_processes, 1, device=device)
            eval_distances = []

            for eval_step in range(args.num_steps_eval):
                with torch.no_grad():
                    _, action, _, eval_recurrent_hidden_states = actor_critic.act(
                        obs, eval_recurrent_hidden_states, eval_masks, deterministic=True)

                # Obser reward and next obs
                obs, reward, done, infos = eval_envs.step(action)

                eval_masks = torch.tensor([[0.0] if done_ else [1.0] for done_ in done],
                                          dtype=torch.float32,
                                          device=device)
                logger.log(msg='eval step reward: {}'.format(reward), level=18)
                logger.log(msg='eval step obs: {}'.format(obs), level=18)

                for info in infos:
                    if 'episode' in info.keys():
                        eval_episode_rewards.append(info['episode']['r'])
                    if 'distance' in info.keys():
                        eval_distances.append(info['distance'])

                rewards.extend(reward)

            if args.episodic:
                logger.info("Evaluation using {} episodes: mean reward {:.5f}\n".
                            format(len(eval_episode_rewards),
                                   np.mean(eval_episode_rewards)))
            else:
                logger.info("Evaluation using {} steps: mean reward {:.5f}\n".
                            format(args.num_steps_eval,
                                   np.mean(rewards)))

            # update info
            log_info.update({
                'mean_eval_reward': np.mean(rewards),
                'eval_average_distance': np.mean(eval_distances)
            })

        if epoch % args.log_interval == 0:
            logger.info('{}:{}  {}'.format(epoch, num_updates, log_info))
            if args.use_wandb:
                import wandb
                wandb.log(log_info)
        env_vector[0].test_mode = True


def test(eval_envs, actor_critic, args, logger):
    logger.info('Evaluate')

    total_rewards = []
    episode_rewards = []

    obs = eval_envs.reset()
    eval_recurrent_hidden_states = torch.zeros(args.num_processes,
                                               actor_critic.recurrent_hidden_state_size, device=device)
    eval_masks = torch.zeros(args.num_processes, 1, device=device)

    for episode_count in range(args.test_episode):
        logger.info(f'Test Episode {episode_count}')
        eval_envs.reset()
        done = False
        while not done:
            with torch.no_grad():
                _, action, _, eval_recurrent_hidden_states = actor_critic.act(
                    obs, eval_recurrent_hidden_states, eval_masks, deterministic=True)

            # Obser reward and next obs
            obs, reward, done, infos = eval_envs.step(action)

            if args.episodic and 'episode' in infos[0].keys():
                episode_rewards.append(infos[0]['episode']['r'])
            else:
                total_rewards.append(reward)

    eval_envs.close()

    logger.info("Evaluation using {} steps: mean reward {:.5f}  episode_mean_reward: {:.5f}\n".format(
            args.test_episode, np.mean(np.asarray(total_rewards)), np.mean(np.asarray(episode_rewards))))


if __name__ == "__main__":
    main()
