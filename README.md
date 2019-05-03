# artisynth_rl
This is the code for **"Muscle Excitation Estimation in Biomechanical Simulation 
Using NAF Reinforcement Learning"** paper submitted to the Computational 
Biomechanics for Medicine XIII, A MICCAI 2018 Workshop, Granada, Spain, 
2018 September 16.

This implementation used an 
[extended version of keras-rl](https://github.com/amir-abdi/keras-rl) to train 
a reinforcement learning agent for point-to-point reaching tasks in 
biomechanical environments.  


## Dependencies

- **artisynth_core**: [ArtiSynth](https://www.artisynth.org/Main/HomePage) is a 
biomechanical modeling environment which supports both rigid bodies and finite 
elements. ArtiSynth can be downloaded from its 
[git repository](https://github.com/artisynth/artisynth_core),
and its installation guide is available 
[here](https://www.artisynth.org/Documentation/InstallGuide). 

- **keras-rl**: keras-rl is a reinforcement learning library build on top of 
the keras library. This library is extended by me to include some new 
functionalities, the details of which are available in the paper. 
Please check out the extended forked version from 
[here](https://github.com/amir-abdi/keras-rl).

- **keras**: Details available [here](https://keras.io).

- **TensorFlow**: Although keras can run with theano as its backend, we have
extensively leveraged the tensorboard features in our implementation; thus, 
we encourage users to use TensorFlow. 

- **java-json**: The message passing between the keras-rl (python) and 
artisynth (java) is through a tcp socket and the packets are sent as serialized
JSON objects. To enable json support in java, include the java-json.jar
(available in lib/ directory) in runtime libraries of the artisynth_rl project.

## Dependencies

- Install ArtiSynth following its 
[installation guide](https://www.artisynth.org/Documentation/InstallGuide)
- [TensorFlow](https://www.tensorflow.org/install/)
- [keras](https://keras.io/#installation)
- [keras-rl](https://github.com/amir-abdi/keras-rl)
- [pytorch](https://pytorch.org/)

Include keras-rl directory as python path for your project. You can do this
either by adding the keras-rl library root to the PYTHONPATH environment 
variable, or add
the library as an external dependency in your IDE.

Add `artisynth_rl/artisynth_src` to Classpath of the artisynth project. 
If you are using eclipse, import the artisynth_rl into eclipse, 
open the **Run Configurations** for the artisynth_core project, 
switch to Classpath tab, and add the artisynth_rl project to the Classpath.

Add `artisynth_core/bin` to the PATH environment variable, so that you can run the `artisynth` command.

## Running

### Step 1
Run ArtiSynth with the following arguments for the point-to-point toy environment:

    artisynth -model artisynth.models.rl.PointModelGenericRl \
        [ -port 7024 -num 6 -demoType 2 -muscleOptLen 0.1 -radius 5 ] \
        -play -noTimeline

Or run with the following for the LumbarSpine environment:

    artisynth -model artisynth.models.lumbarSpine.RlInvLumbarSpine \
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
