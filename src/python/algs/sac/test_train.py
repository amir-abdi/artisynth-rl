"""
Originally implemented by: https://github.com/pranz24/pytorch-soft-actor-critic
Check LICENSE for details
"""
import datetime
import itertools
from tensorboardX import SummaryWriter
import os
import pickle

from algs.sac.replay_memory import ReplayMemory
from common.config import setup_logger
from common.utilities import get_lr_pytorch


def train(env, agent, args, configs, memory=None, global_episodes=0):
    logger = setup_logger()

    # TesnorboardX
    if args.use_tensorboard:
        writer = SummaryWriter(
            logdir='{}/{}_SAC_{}_{}_{}'.format(configs.tensorboard_log_directory,
                                               datetime.datetime.now().strftime("%Y-%m-%d_%H-%M-%S"), args.env,
                                               args.policy, "autotune" if args.automatic_entropy_tuning else ""))

    # Memory
    memory = memory or ReplayMemory(args.replay_size)

    # Training Loop
    global_steps = 0

    for global_episodes in itertools.count(start=global_episodes, step=1):
        episode_reward = 0
        episode_steps = 0
        done = False

        state = env.reset()

        critic_1_loss_total = 0
        critic_2_loss_total = 0
        policy_loss_total = 0
        ent_loss_total = 0
        alpha_total = 0
        while not done:
            if len(memory) < args.start_steps and global_steps < args.start_steps:
                action = env.action_space.sample()  # Sample random action
            else:
                action = agent.select_action(state)  # Sample action from policy
            if len(memory) > args.batch_size:
                for i in range(args.updates_per_step):  # Number of updates per step in environment
                    critic_1_loss, critic_2_loss, policy_loss, ent_loss, alpha = \
                        agent.update_parameters(memory, args.batch_size)  # update all parameters

                    critic_1_loss_total += critic_1_loss
                    critic_2_loss_total += critic_2_loss
                    policy_loss_total += policy_loss
                    ent_loss_total += ent_loss
                    alpha_total += alpha

            next_state, reward, done, _ = env.step(action)  # Step
            episode_steps += 1
            global_steps += 1
            episode_reward += reward

            # Ignore the "done" signal if it comes from hitting the time horizon.
            # (https://github.com/openai/spinningup/blob/master/spinup/algos/sac/sac.py)
            mask = 1 if episode_steps == env.reset_step else float(not done)

            memory.push(state, action, reward, next_state, mask)  # Append transition to memory
            state = next_state
        # end of episode

        # The following values are a bit off for the first episode as we have no updates for len(memory) < batch_size
        critic_1_loss_total /= episode_steps
        critic_2_loss_total /= episode_steps
        policy_loss_total /= episode_steps
        episode_reward /= episode_steps
        ent_loss_total /= episode_steps
        alpha_total /= episode_steps

        if global_episodes % args.episode_log_interval == 0:
            print("Episode: {}, total numsteps: {}, episode steps: {}, reward: {}".format(
                global_episodes, global_steps, episode_steps, round(episode_reward, 2)))
            if args.use_tensorboard:
                writer.add_scalar('reward/train', episode_reward, global_episodes)
                writer.add_scalar('loss/critic_1', critic_1_loss_total, global_episodes)
                writer.add_scalar('loss/critic_2', critic_2_loss_total, global_episodes)
                writer.add_scalar('loss/policy', policy_loss_total, global_episodes)
                writer.add_scalar('loss/entropy_loss', ent_loss_total, global_episodes)
                writer.add_scalar('entropy_temprature/alpha', alpha_total, global_episodes)
            if args.use_wandb:
                import wandb
                wandb.log({'episode_reward': episode_reward,
                           'loss/critic_1': critic_1_loss_total / args.updates_per_step,
                           'loss/critic_2': critic_2_loss_total / args.updates_per_step,
                           'loss/policy': policy_loss_total / args.updates_per_step,
                           'loss/entropy_loss': ent_loss_total / args.updates_per_step,
                           'entropy_temprature/alpha': alpha_total / args.updates_per_step,
                           'lr': get_lr_pytorch(agent.policy_optim)},
                          step=global_episodes)

        if global_episodes % args.eval_interval == args.eval_interval - 1:
            avg_reward, infos = _test(env, agent, args.eval_episode)
            if args.use_tensorboard:
                writer.add_scalar('eval/avg_reward', avg_reward, global_episodes)
                for key, val in infos.items():
                    writer.add_scalar(f'eval/{key}', val, global_episodes)
            if args.use_wandb:
                import wandb
                wandb.log({'eval/avg_reward': avg_reward}, step=global_episodes)
                for key, val in infos.items():
                    wandb.log({f'eval/{key}': val}, step=global_episodes)

        if global_episodes % args.save_interval == args.save_interval - 1:
            agent_save_path = os.path.join(configs.trained_directory, 'agent')
            agent.global_episode = global_episodes + 1
            # torch.save(agent, agent_save_path)
            agent.save_model(agent_save_path, global_episodes)
            logger.info(f'model saved: {agent_save_path}')

            memory_path = os.path.join(configs.trained_directory, 'memory')
            pickle.dump(memory, open(memory_path, 'wb'))
            logger.info(f'memory saved: {memory_path}')
            print('------------------')

        if global_steps > args.num_steps:  # end of training
            break

    env.close()


def test(env, agent, args):
    # todo: add setSeed to api
    env.seed(args.seed)
    _test(env, agent, args.test_episode)
    env.close()


def _test(env, agent, episodes):
    avg_reward = 0.
    infos = {}
    for episode_count in range(episodes):
        state = env.reset()
        episode_reward = 0
        done = False
        episode_iter_count = 0
        info_episode_avg = {}
        info_episode_final = {}
        while not done:
            action = agent.select_action(state, eval_mode=True)
            next_state, reward, done, info = env.step(action)
            episode_reward += reward
            state = next_state
            episode_iter_count += 1

            for key, val in info.items():
                info_episode_avg['avg_' + key] = info_episode_avg.get('avg_' + key, 0) + val
                info_episode_final['final_' + key] = val

        episode_reward /= episode_iter_count
        avg_reward += episode_reward
        episode_print_str = f'{episode_count}/{episodes} reward:{episode_reward:.3f}'

        for key in info_episode_avg.keys():
            info_episode_avg[key] /= episode_iter_count
            infos[key] = infos.get(key, 0) + info_episode_avg[key]
            episode_print_str += f'  {key}:{info_episode_avg[key]:.3f}'

        for key in info_episode_final.keys():
            infos[key] = infos.get(key, 0) + info_episode_final[key]
            episode_print_str += f'  {key}:{info_episode_final[key]:.3f}'

        print(episode_print_str)

    avg_reward /= episodes
    print_str = f'Test #Episodes: {episodes}, avg_reward: {round(avg_reward, 3)}'
    for key in infos.keys():
        infos[key] /= episodes
        print_str += f' {key}:{infos[key]:.3f}'

    print("----------------------------------------")
    print(print_str)
    print("----------------------------------------")
    return avg_reward, infos
