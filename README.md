# Reinforcement Learning for ArtiSynth

This repository holds the plugin for the biomechanical simulation 
environment of [ArtiSynth](https://www.artisynth.org).
The  plugin, implemented in Java, exposes the state of a Reinforcement Learning (RL)
model through a RESTful API.

This repository also holds sample RL model for ArtiSynth (in Java) and 
 and their corresponding OpenAI Gym environment (in Python).
The environments follow the protocols of Gym environments and gives you the 
flexibility to implement the agent with any deep framework of your choice.

**Researchers are free to use `artisynth_rl_restapi` in their work with 
proper credits to the author(s). 
Please contact authors for details.** 

## Dependencies

- **ArtiSynth**: [ArtiSynth](https://www.artisynth.org/Main/HomePage) is a 
biomechanical modeling environment which supports both rigid bodies and finite 
elements. ArtiSynth can be downloaded from its 
[git repository](https://github.com/artisynth/artisynth_core),
and its installation guide is available 
[here](https://www.artisynth.org/Documentation/InstallGuide).

- **Maven2**: Maven is a software project manager. 
Maven will then install the rest of the Java dependencies.

- **Eclipse**: The ArtiSynth project and its libraries are fully 
integrated with the Eclipse java compiler. 

     

#### Other Dependencies

In order to run the sample toy projects the following dependencies 
need to be installed:

- **keras**: Installation guide is available [here](https://keras.io).

- **TensorFlow**:  Installation guide is available [here](
https://www.tensorflow.org/install)

- **PyTorch** Installation guide is available [here](
https://pytorch.org/get-started/locally/)

- **keras-rl**: Keras implementation of some RL algorithms
[here](https://github.com/keras-rl/keras-rl).

- **pytorch-a2c-ppo-acktr-gail**: PyTorch implementation of some RL algorithms 
([here](https://github.com/ikostrikov/pytorch-a2c-ppo-acktr-gail)).



## Installation

1- Install the [Eclipse IDE](https://www.eclipse.org/downloads/)

2- Install *ArtiSynth* following its [installation guide](https://www.artisynth.org/Documentation/InstallGuide).

3- Import the `artisynth_core` project (from step 2) into Eclipse.

4- Import the `artisynth_rl_models` and `artisynth_rl_restapi` projects (from this repository)
 into Eclipse.

5- Set the environment variable `$ARTISYNTH_HOME` to the 
`artisynth_core` directory.

6- Install Maven2: `sudo apt-get install maven2`   

7- Run the command:    `source setup.sh`

- If you used any of the third-party python libraries 
(keras-rl, pytorch-a2c-ppo-acktr-gail, etc), make sure to include them in your PYTHONPATH.


### Check installation

- Check that ArtiSynth runs successfully by executing the command: `artisynth`

- Run ArtiSynth with one of the RL environments, e.g.: 
`artisynth -model artisynth.models.rl.lumbarspine.RlLumbarSpineDemo`


## Run and Train

Once you have the keras-rl library installed, 
to train the point2point reaching toy project, run:

    bash scripts/point2point.sh

This will fire up ArtiSynth with the RlPoint2PointModel instantiated 
and starts training. 
Change the `artisynth-args` argument to initiate different models.

You can also run ArtiSynth separately by executing the command: 

    artisynth -model artisynth.models.rl.MODELPACKAGE.MODELNAME \
        [ -port 8080 -FLAG1 FLAGVALUE1 -FLAG2 FLAGVALUE2... ] \
        -play -noTimeline

And then run the `point2point.sh` bash file with `init-artisynth=false`.
Make sure to set the `port` argument to the same port where you
are running ArtiSynth.

You can train the LumbarSpine model by running `bash scripts/lumbarspine.sh`.
Similarly, ArtiSynth can be independently initiated with the 
LumbarSpine model by running:

    artisynth -model artisynth.models.rl.lumbarspine.RlLumbarSpineDemo \
        [ -port 8080  ] \
        -play -noTimeline
  

Training results and logs are stored in 4 directories, namely

- trained: stores the trained model
- log_agent: stores the agent-related logs with timestamp
- log_env: stores the environment logs with timestamp
- log_tb: stores the tensorboard logs which can be visualized during training 
by tensorboard and setting the `--logdir=logs_tb/TB_LOGGING_DIR`.

The above 4 directories are created in the parent directory of where 
`main_keras.py` is executed. In the `src/config.py` it is 
assumed that the main file is executed from inside the `src` folder and
the 4 directories are made in the artisynth_rl root.   

### Available Environments

#### Point2Point
    artisynth -model artisynth.models.rl.point2point.RlPoint2PointDemo \
        [ -port 8080 -num 6 -demoType 2d -muscleOptLen 0.1 -radius 5 ] \
        -play -noTimeline
        
#### LumbarSpine

    artisynth -model artisynth.models.rl.lumbarspine.RlLumbarSpineDemo \
        [ -port 8080 ] \
        -play -noTimeline

#### Jaw
The Jaw model is modified from the 
[Dynjaw package](https://github.com/artisynth/artisynth_models/tree/69fb58f521cead7b48250f320177475fcbea5ddc/src/artisynth/models/dynjaw) 
in ArtiSynth, originally 
developed by Ian Stavness (@stavness) form the University of Saskatchewan, 
and later extended by Benedikt Sagl (Medical University of Vienna).
The original jaw model is available in the 
[artisynth_models](https://github.com/artisynth/artisynth_models)
repository.

    artisynth -model artisynth.models.rl.jaw.RlJawDemo \
    [  -port 8080 -disc false -condyleConstraints false -condylarCapsule true ]  \
    -play -noTimeline


**Demo**: You can watch a demo of the trained jaw model here   
[![Jaw Model Demo](https://img.youtube.com/vi/E9Ix0q5frSQ/0.jpg)](https://www.youtube.com/watch?v=E9Ix0q5frSQ) 

### Testing

To test a trained model, set the `--load-path` to the saved model
and set `--test=true`. 
  
#### Sample demos
[![Point to point tracking video](https://img.youtube.com/vi/UqHt4KbsaII/0.jpg)](https://www.youtube.com/watch?v=UqHt4KbsaII) 

[![Out of domain tracking](https://img.youtube.com/vi/PQHBK3C28Q8/0.jpg)](https://www.youtube.com/watch?v=PQHBK3C28Q8)
