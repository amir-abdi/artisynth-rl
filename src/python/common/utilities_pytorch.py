from torch.optim.lr_scheduler import _LRScheduler


class ExponentialLRWithMin(_LRScheduler):
    """Set the learning rate of each parameter group to the initial lr decayed
    by gamma every epoch. When last_epoch=-1, sets initial lr as lr.
    Learning rate is always >= min

    Args:
        optimizer (Optimizer): Wrapped optimizer.
        gamma (float): Multiplicative factor of learning rate decay.
        last_epoch (int): The index of last epoch. Default: -1.
    """

    def __init__(self, optimizer, gamma, last_epoch=-1, min=0):
        self.gamma = gamma
        self.min = min
        super(ExponentialLRWithMin, self).__init__(optimizer, last_epoch)

    def get_lr(self):
        return [max(base_lr * self.gamma ** self.last_epoch, self.min)
                for base_lr in self.base_lrs]
