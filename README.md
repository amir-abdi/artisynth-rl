# Reinforcement Learning for ArtiSynth

This repository holds the plugin for the biomechanical simulation 
environment of [ArtiSynth](https://www.artisynth.org).
The  plugin, implemented in Java, exposes the state of a Reinforcement Learning (RL)
model through a RESTful API.

This repository also holds sample RL model for ArtiSynth (in Java) and 
 and their corresponding OpenAI Gym environment (in Python).
The environments follow the protocols of Gym environments and gives you the 
flexibility to implement the agent with any deep framework of your choice.

## Dependencies

- **ArtiSynth**: [ArtiSynth](https://www.artisynth.org/Main/HomePage) is a 
biomechanical modeling environment which supports both rigid bodies and finite 
elements. ArtiSynth can be downloaded from its 
[git repository](https://github.com/artisynth/artisynth_core),
and its installation guide is available 
[here](https://www.artisynth.org/Documentation/InstallGuide).

- **Maven2**: Maven is a software project manager. 
Maven will then install the rest of the Java dependencies. 

     

#### Other Dependencies

In order to run the sample toy projects the following dependencies 
need to be installed:

- **keras**: Installation guide is available [here](https://keras.io).

- **TensorFlow**:  Installation guide is available [here](
https://www.tensorflow.org/install)

- **PyTorch** Installation guide is available [here](
https://pytorch.org/get-started/locally/)

- **keras-rl**: Keras implementation of some RL algorithms. 
The library is forked slightly extended by our team 
[here](https://github.com/amir-abdi/keras-rl).

- **pytorch-a2c-ppo-acktr-gail**: PyTorch implementation of some RL algorithms.
The repository can be cloned from [here](https://github.com/ikostrikov/pytorch-a2c-ppo-acktr-gail).



## Installation

- Install *ArtiSynth* following its [installation guide](https://www.artisynth.org/Documentation/InstallGuide).

- Set the environment variable `$ARTISYNTH_HOME` to the 
`artisynth_core` directory.

- Install Maven2: `sudo apt-get install maven2`   

- Run the command:    `source setup.sh`

- If you used any of the third-party python libraries 
(keras-rl, pytorch-a2c-ppo-acktr-gail, etc), make sure to include them in your PYTHONPATH.


### Check installation

- Check that ArtiSynth runs successfully by executing the command: `artisynth`

- Run ArtiSynth with one of the RL environments, e.g.: 
`artisynth -model artisynth.models.lumbarSpine.RlLumbarSpineModel`


## Running

### Step 1
Run ArtiSynth with the following arguments for the point-to-point toy environment:

    artisynth -model artisynth.models.rl.PointModelGenericRl \
        [ -port 7024 -num 6 -demoType 2 -muscleOptLen 0.1 -radius 5 ] \
        -play -noTimeline

Or run with the following for the LumbarSpine environment:

    artisynth -model artisynth.models.lumbarSpine.RlLumbarSpineModel \
        [ -port 7024 ] 
        -play -noTimeline
        
Or to run the angular version:

    artisynth -model artisynth.models.lumbarSpine.RlInvLumbarSpineAngular \
            [ -port 7024 ] 
            -play -noTimeline
    
where 
- `port` is the port number for the tcp socket and should 
match the port set in `src/point_model2d_naf_main.py`, 
- `num` sets the number of muscles in the model,
- `demoType` defines the dimensionality of the model and support 2 (for 2D)
and 3 (for 3D) models. In the 3D model. When `demoType` is set to 3 (3D),
`num` is ignored and the model is predefined to have 8 muscles.
- `MuscleOptLen` defines the optimal lengths of muscles at which the apply
no force on the particle.
- `radius` defines the radius of the circle on the perimeter of which
the muscles are arranged.
- `play` hints artisynth to play immediately after loading the model.
- `noTimeline` removes the timeline from artisynth as it has no use for our
reinforcement learning cause.

  
### Step 2 - Training
Run `src/point_model2d_naf_main.py` with the same environment parameters 
such as `NUM_MUSCLES`, `PORT`, and `DOF_OBSERVATIONS`. 

Training results and logs are stored in 4 directories, namely

- trained: stores the trained model
- log_agent: stores the agent-related logs with timestamp
- log_env: stores the environment logs with timestamp
- log_tb: stores the tensorboard logs which can be visualized during training 
by tensorboard and setting the `--logdir=logs_tb/TB_LOGGING_DIR`.

The above 4 directories are created in the parent directory of where 
`point_model2d_naf_main.py` is executed. In the `src/config.py` it is 
assumed that the main file is executed from inside the `src` folder and
the 4 directories are made in the artisynth_rl root.   

### Step 3 - Testing
Once the model was successfully trained (the agent was constantly reaching
the success state), call the `main` function in  `src/point_model2d_naf_main.py`
with `'test'` as input instead of `'train'` and see the results. 

## Results

Once the training is complete, the model (agent) will be able to move the 
particle by finding the correct muscle activations to reach its destination.
  
[![Point to point tracking video](https://img.youtube.com/vi/UqHt4KbsaII/0.jpg)](https://www.youtube.com/watch?v=UqHt4KbsaII) 

[![Out of domain tracking](https://img.youtube.com/vi/PQHBK3C28Q8/0.jpg)](https://www.youtube.com/watch?v=PQHBK3C28Q8)
