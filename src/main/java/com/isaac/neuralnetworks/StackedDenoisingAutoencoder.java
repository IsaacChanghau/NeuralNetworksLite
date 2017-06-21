package com.isaac.neuralnetworks;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StackedDenoisingAutoencoder {
	
	public int nIn;
    public int[] hiddenLayerSizes;
    public int nOut;
    public int nLayers;
    public DenoisingAutoencoder[] daLayers;
    public HiddenLayer[] sigmoidLayers;
    public LogisticRegression logisticLayer;
    public Random rng;

    public StackedDenoisingAutoencoder(int nIn, int[] hiddenLayerSizes, int nOut, Random rng) {
        this.nIn = nIn;
        this.hiddenLayerSizes = hiddenLayerSizes;
        this.nOut = nOut;
        this.nLayers = hiddenLayerSizes.length;
        this.sigmoidLayers = new HiddenLayer[nLayers];
        this.daLayers = new DenoisingAutoencoder[nLayers];
        this.rng = rng == null ? new Random(1234) : rng;
        // construct multi-layer
        for (int i = 0; i < nLayers; i++) {
            int nIn_;
            if (i == 0)
            	nIn_ = nIn;
            else
            	nIn_ = hiddenLayerSizes[i-1];
            // construct hidden layers with sigmoid function, weight matrices will be shared with RBM layers
            sigmoidLayers[i] = new HiddenLayer(nIn_, hiddenLayerSizes[i], null, null, rng, "sigmoid");
            // construct DA layers
            // "NOTICE" -- the first null below: original is "sigmoidLayers[i].b"
            daLayers[i] = new DenoisingAutoencoder(nIn_, hiddenLayerSizes[i], sigmoidLayers[i].W, null, null, rng);
        }
        // logistic regression layer for output
        logisticLayer = new LogisticRegression(hiddenLayerSizes[nLayers-1], nOut);
    }

    public void pretrain(double[][][] X, int minibatchSize, int minibatch_N, int epochs, double learningRate, 
    		double corruptionLevel) {
        for (int layer = 0; layer < nLayers; layer++) {
            for (int epoch = 0; epoch < epochs; epoch++) {
                for (int batch = 0; batch < minibatch_N; batch++) {
                    double[][] X_ = new double[minibatchSize][nIn];
                    double[][] prevLayerX_;
                    // Set input data for current layer
                    if (layer == 0) {
                        X_ = X[batch];
                    } else {
                        prevLayerX_ = X_;
                        X_ = new double[minibatchSize][hiddenLayerSizes[layer-1]];
                        for (int i = 0; i < minibatchSize; i++) {
                            X_[i] = sigmoidLayers[layer-1].output(prevLayerX_[i]);
                        }
                    }
                    daLayers[layer].train(X_, minibatchSize, learningRate, corruptionLevel);
                }
            }
        }
    }

    public void finetune(double[][] X, int[][] T, int minibatchSize, double learningRate) {
        List<double[][]> layerInputs = new ArrayList<>(nLayers + 1);
        layerInputs.add(X);
        double[][] Z = new double[0][0];
        double[][] dY;
        // forward hidden layers
        for (int layer = 0; layer < nLayers; layer++) {
            double[] x_;  // layer input
            double[][] Z_ = new double[minibatchSize][hiddenLayerSizes[layer]];
            for (int n = 0; n < minibatchSize; n++) {
                if (layer == 0) {
                    x_ = X[n];
                } else {
                    x_ = Z[n];
                }
                Z_[n] = sigmoidLayers[layer].forward(x_);
            }
            Z = Z_;
            layerInputs.add(Z.clone());
        }
        // forward & backward output layer
        dY = logisticLayer.train(Z, T, minibatchSize, learningRate);
        // backward hidden layers
        double[][] Wprev;
        double[][] dZ = new double[0][0];
        for (int layer = nLayers - 1; layer >= 0; layer--) {
            if (layer == nLayers - 1) {
                Wprev = logisticLayer.W;
            } else {
                Wprev = sigmoidLayers[layer+1].W;
                dY = dZ.clone();
            }
            dZ = sigmoidLayers[layer].backward(layerInputs.get(layer), layerInputs.get(layer+1), dY, Wprev, minibatchSize, learningRate);
        }
    }

    public Integer[] predict(double[] x) {
        double[] z = new double[0];
        for (int layer = 0; layer < nLayers; layer++) {
            double[] x_;
            if (layer == 0) {
                x_ = x;
            } else {
                x_ = z.clone();
            }
            z = sigmoidLayers[layer].forward(x_);
        }
        return logisticLayer.predict(z);
    }
}
