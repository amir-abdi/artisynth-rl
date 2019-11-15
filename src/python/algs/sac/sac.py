"""
Originally implemented by: https://github.com/pranz24/pytorch-soft-actor-critic
Check LICENSE for details
"""
import torch
import torch.nn.functional as F
from torch.optim import Adam
from algs.sac.utils import soft_update, hard_update
from algs.sac.model import GaussianPolicy, QNetwork, DeterministicPolicy
from common.utilities import ExponentialLRWithMin


class SAC:
    def __init__(self, num_inputs, action_space, args):
        super(SAC, self).__init__()

        self.optims = dict()
        self.models = dict()
        self.lr_schedulers = []

        self.gamma = args.gamma
        self.tau = args.tau
        self.alpha = args.alpha
        self.updates_count = 0

        self.policy_type = args.policy
        self.target_update_interval = args.target_update_interval
        self.automatic_entropy_tuning = args.automatic_entropy_tuning
        self.device = torch.device("cuda" if args.cuda else "cpu")

        self.critic = QNetwork(num_inputs, action_space.shape[0], args.hidden_size).to(device=self.device)
        self.critic_optim = Adam(self.critic.parameters(), lr=args.lr)
        self.lr_schedulers.append(ExponentialLRWithMin(self.critic_optim, args.lr_gamma, min=args.lr_min))
        self.optims['critic_optim'] = self.critic_optim
        self.models['critic'] = self.critic

        self.critic_target = QNetwork(num_inputs, action_space.shape[0], args.hidden_size).to(self.device)
        hard_update(self.critic_target, self.critic)

        if self.policy_type == "Gaussian":
            # Target Entropy = −dim(A) (e.g. , -6 for HalfCheetah-v2) as given in the paper
            if self.automatic_entropy_tuning:
                self.target_entropy = -torch.prod(torch.Tensor(action_space.shape).to(self.device)).item()
                self.log_alpha = torch.zeros(1, requires_grad=True, device=self.device)
                self.alpha_optim = Adam([self.log_alpha], lr=args.lr)
                self.lr_schedulers.append(ExponentialLRWithMin(self.alpha_optim, args.lr_gamma, min=args.lr_min))
                self.models['alpha_optim'] = self.alpha_optim

            self.policy = GaussianPolicy(num_inputs, action_space.shape[0], args.hidden_size, action_space).to(
                self.device)
            self.policy_optim = Adam(self.policy.parameters(), lr=args.lr)
        else:
            self.alpha = 0
            self.automatic_entropy_tuning = False
            self.policy = DeterministicPolicy(num_inputs, action_space.shape[0], args.hidden_size, action_space).to(
                self.device)
            self.policy_optim = Adam(self.policy.parameters(), lr=args.lr)
        self.lr_schedulers.append(ExponentialLRWithMin(self.policy_optim, args.lr_gamma, min=args.lr_min))
        self.optims['policy_optim'] = self.policy_optim
        self.models['policy'] = self.policy

    def select_action(self, state, eval_mode=False):
        state = torch.FloatTensor(state).to(self.device).unsqueeze(0)
        if not eval_mode:
            action, _, _ = self.policy.sample(state)
        else:
            _, _, action = self.policy.sample(state)
        return action.detach().cpu().numpy()[0]

    def update_parameters(self, memory, batch_size):
        # Sample a batch from memory
        state_batch, action_batch, reward_batch, next_state_batch, mask_batch = memory.sample(batch_size=batch_size)

        state_batch = torch.FloatTensor(state_batch).to(self.device)
        next_state_batch = torch.FloatTensor(next_state_batch).to(self.device)
        action_batch = torch.FloatTensor(action_batch).to(self.device)
        reward_batch = torch.FloatTensor(reward_batch).to(self.device).unsqueeze(1)
        mask_batch = torch.FloatTensor(mask_batch).to(self.device).unsqueeze(1)

        with torch.no_grad():
            next_state_action, next_state_log_pi, _ = self.policy.sample(next_state_batch)
            qf1_next_target, qf2_next_target = self.critic_target(next_state_batch, next_state_action)
            min_qf_next_target = torch.min(qf1_next_target, qf2_next_target) - self.alpha * next_state_log_pi
            next_q_value = reward_batch + mask_batch * self.gamma * min_qf_next_target

        # Two Q-functions to mitigate positive bias in the policy improvement step
        qf1, qf2 = self.critic(state_batch, action_batch)
        qf1_loss = F.mse_loss(qf1, next_q_value)  # JQ = 𝔼(st,at)~D[0.5(Q1(st,at) - r(st,at) - γ(𝔼st+1~p[V(st+1)]))^2]
        qf2_loss = F.mse_loss(qf2, next_q_value)  # JQ = 𝔼(st,at)~D[0.5(Q1(st,at) - r(st,at) - γ(𝔼st+1~p[V(st+1)]))^2]

        action_pi, log_pi, _ = self.policy.sample(state_batch)

        qf1_pi, qf2_pi = self.critic(state_batch, action_pi)
        min_qf_pi = torch.min(qf1_pi, qf2_pi)

        # Jπ = 𝔼st∼D,εt∼N[α * logπ(f(εt;st)|st) − Q(st,f(εt;st))]
        policy_loss = ((self.alpha * log_pi) - min_qf_pi).mean()

        self.critic_optim.zero_grad()
        qf1_loss.backward()
        self.critic_optim.step()

        self.critic_optim.zero_grad()
        qf2_loss.backward()
        self.critic_optim.step()

        self.policy_optim.zero_grad()
        policy_loss.backward()
        self.policy_optim.step()

        if self.automatic_entropy_tuning:
            alpha_loss = -(self.log_alpha * (log_pi + self.target_entropy).detach()).mean()
            self.alpha_optim.zero_grad()
            alpha_loss.backward()
            self.alpha_optim.step()

            self.alpha = self.log_alpha.exp()
            alpha_tlogs = self.alpha.clone()  # For TensorboardX logs
        else:
            alpha_loss = torch.tensor(0.).to(self.device)
            alpha_tlogs = torch.tensor(self.alpha)  # For TensorboardX logs

        if self.updates_count % self.target_update_interval == 0:
            soft_update(self.critic_target, self.critic, self.tau)

        for scheduler in self.lr_schedulers:
            scheduler.step()

        return qf1_loss.item(), qf2_loss.item(), policy_loss.item(), alpha_loss.item(), alpha_tlogs.item()

    def save_model(self, filepath, global_episodes):
        model_states = dict()
        optim_states = dict()

        # neural models
        for key, value in self.models.items():
            model_states.update({key: value.state_dict()})

        # optimizers' states
        for key, value in self.optims.items():
            optim_states.update({key: value.state_dict()})

        states = {'global_episode_num': global_episodes,  # to avoid saving right after loading
                  'model_states': model_states,
                  'optim_states': optim_states}

        # make sure KeyboardInterrupt exceptions don't mess up the model saving process
        while True:
            try:
                with open(filepath, 'wb+') as f:
                    torch.save(states, f)
                break
            except KeyboardInterrupt:
                pass

    def load_model(self, filepath, load_optim=True):
        with open(filepath, 'rb') as f:
            checkpoint = torch.load(f)

        for key, value in self.models.items():
            value.load_state_dict(checkpoint['model_states'][key], strict=False)

        if load_optim:
            # todo: make sure learning rate loads from optim_state
            self.lr_schedulers.clear()
            for key, value in self.optims.items():
                value.load_state_dict(checkpoint['optim_states'][key])

        return checkpoint['global_episode_num']
