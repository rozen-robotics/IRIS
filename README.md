# Wheelchair Control using Your Eyes on Android

A simple high performance android application that captures feed from camera and detects eyes' gaze.

![ezgif-4-7de083e1ec](https://github.com/rozen-team/IRIS/assets/43082655/585ec21b-af57-42c6-9c68-a134eb08f48b)

> Important info: gif image reduces framerate. Application can accelerate up to 30 fps.

## Build & Run

1. Clone this repo: `git clone https://github.com/rozen-team/IRIS`.
2. Open using [Android Studio](https://developer.android.com/studio).
3. Connect your Android smartphone or setup simulator.
4. Click `Run 'app'`.

## Acceleration

App uses NPU and GPU acceletaion to increase neural network performace. Be sure to use smartphone with good NPU/GPU chip. CPU runtime was not tested yet.

Prefer using NNAPI capatable devices. Modern smartphones often has neural processing unit. Check more info at your manufacturer documentation.

App performance was tested on `Google Pixel 6a`.

## Dataset

[Download link](https://app.roboflow.com/pedro-etuzo/iris-qcubp/4)

The dataset was collected from smartphone camera and VisionRide Googles. 

Dataset has 3 classes:

- eye-closed
- eye-opened
- pupil (iris)

Using Roboflow dataset was upscaled and augmentations were applied. You can check more info on dataset page (see link above).

![image](https://github.com/rozen-team/IRIS/assets/43082655/dd5d0072-921b-41f3-bbcc-0937f36b7a49)

## Model Training

Use Google Colab or other Jupyter server to train model on a custom dataset.

You can see more information about model (`EffientDet`) on [this Kaggle's page](https://www.kaggle.com/models/tensorflow/efficientdet).

Colab Notebook that was used to train net (instructions inside):

https://colab.research.google.com/drive/1QOh-vjV1L-7QJXVIwnOjXFZuTnrFYMSL?usp=sharing

## Update Model

After training you can upload your model to application. 

Put your model to `assets` folder under name `model1.tflite`. 

![image](https://github.com/rozen-team/IRIS/assets/43082655/64b173b2-3b45-49ff-aadc-ddfd0dbd995d)

You can optionally use custom name and change model name reference in `TFObjectDetector.initialize()` function's options.

## TODO

- [x] Train efficientDet model.
- [x] Implement eyes detection from camera feed.
- [ ] Create frendly UI (startup options, settings, etc.).
- [ ] Connect to remote camera (from VisionRide Googles).
- [ ] Publish detected objects' data to ROS topics on RPi.
- [ ] Make AI process to run in background.
- [ ] Optimize model (quantize).
