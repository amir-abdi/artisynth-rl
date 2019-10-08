import datetime
import itertools
import torch
from tensorboardX import SummaryWriter
from algs.sac.replay_memory import ReplayMemory
import os

from common.config import setup_logger


def train(env, agent, args, configs):
    logger = setup_logger()

    # TesnorboardX
    writer = SummaryWriter(
        logdir='{}/{}_SAC_{}_{}_{}'.format(configs.tensorboard_log_directory,
                                           datetime.datetime.now().strftime("%Y-%m-%d_%H-%M-%S"), args.env,
                                           args.policy, "autotune" if args.automatic_entropy_tuning else ""))

    # Memory
    memory = ReplayMemory(args.replay_size)

    # Training Loop
    global_steps = 0
    updates = 0

    for i_episode in itertools.count(start=agent.global_episode, step=1):
        episode_reward = 0
        episode_steps = 0
        done = False

        state = env.reset()

        while not done:
            if args.start_steps > global_steps:
                action = env.action_space.sample()  # Sample random action
            else:
                action = agent.select_action(state)  # Sample action from policy

            if len(memory) > args.batch_size:
                # Number of updates per step in environment
                critic_1_loss_total = 0
                critic_2_loss_total = 0
                policy_loss_total = 0
                ent_loss_total = 0
                alpha_total = 0

                for i in range(args.updates_per_step):
                    # Update parameters of all the networks
                    critic_1_loss, critic_2_loss, policy_loss, ent_loss, alpha = \
                        agent.update_parameters(memory, args.batch_size, updates)

                    writer.add_scalar('loss/critic_1', critic_1_loss, updates)
                    writer.add_scalar('loss/critic_2', critic_2_loss, updates)
                    writer.add_scalar('loss/policy', policy_loss, updates)
                    writer.add_scalar('loss/entropy_loss', ent_loss, updates)
                    writer.add_scalar('entropy_temprature/alpha', alpha, updates)

                    critic_1_loss_total += critic_1_loss
                    critic_2_loss_total += critic_2_loss
                    policy_loss_total += policy_loss
                    ent_loss_total += ent_loss
                    alpha_total += alpha

                    if args.use_wandb:
                        import wandb
                        wandb.log({'loss/critic_1': critic_1_loss_total / args.updates_per_step,
                                   'loss/critic_2': critic_2_loss_total / args.updates_per_step,
                                   'loss/policy': policy_loss_total / args.updates_per_step,
                                   'loss/entropy_loss': ent_loss_total / args.updates_per_step,
                                   'entropy_temprature/alpha': alpha_total / args.updates_per_step},
                                  step=i_episode)
                    updates += 1

            next_state, reward, done, _ = env.step(action)  # Step
            episode_steps += 1
            global_steps += 1
            episode_reward += reward

            # Ignore the "done" signal if it comes from hitting the time horizon.
            # (https://github.com/openai/spinningup/blob/master/spinup/algos/sac/sac.py)
            mask = 1 if episode_steps == env.reset_step else float(not done)

            memory.push(state, action, reward, next_state, mask)  # Append transition to memory

            state = next_state

        if global_steps > args.num_steps:
            break

        if i_episode % args.episode_log_interval == 0:
            writer.add_scalar('reward/train', episode_reward, i_episode)
            print("Episode: {}, total numsteps: {}, episode steps: {}, reward: {}".format(i_episode, global_steps,
                                                                                          episode_steps,
                                                                                          round(episode_reward, 2)))
        if args.use_wandb:
            import wandb
            wandb.log({'episode_reward': episode_reward}, step=i_episode)

        if i_episode % args.eval_interval == 0:
            avg_reward = 0.
            episodes = args.eval_episode
            for _ in range(episodes):
                state = env.reset()
                episode_reward = 0
                done = False
                while not done:
                    action = agent.select_action(state, eval=True)

                    next_state, reward, done, _ = env.step(action)
                    episode_reward += reward

                    state = next_state
                avg_reward += episode_reward
            avg_reward /= episodes

            writer.add_scalar('avg_reward/test', avg_reward, i_episode)
            if args.use_wandb:
                import wandb
                wandb.log({'avg_test_reward': avg_reward}, step=i_episode)

            print("----------------------------------------")
            print("Test Episodes: {}, Avg. Reward: {}".format(episodes, round(avg_reward, 2)))
            print("----------------------------------------")

        if i_episode % args.save_interval == 0:
            path = os.path.join(configs.trained_directory, 'last')
            agent.global_episode = i_episode
            torch.save(agent.state_dict(), path)
            logger.info(f'model saved: {path}')
            print('------------------')

    env.close()


def test(env, agent, args, configs):
    logger = setup_logger()

    avg_reward = 0.
    episodes = args.test_episode
    for episode_count in range(episodes):
        state = env.reset()
        episode_reward = 0
        done = False
        episode_iter_count = 0
        while not done:
            action = agent.select_action(state, eval=True)

            next_state, reward, done, _ = env.step(action)
            episode_reward += reward

            state = next_state
            episode_iter_count += 1
        episode_reward /= episode_iter_count
        logger.info(f'{episode_count}/{episodes}  reward:{episode_reward}')
        avg_reward += episode_reward
    avg_reward /= episodes

    print("Test Episodes: {}, Avg. Reward: {}".format(episodes, round(avg_reward, 2)))
    print("----------------------------------------")

    env.close()
