package org.deeplearning4j.nn.layers;



import org.deeplearning4j.berkeley.Pair;
import org.deeplearning4j.nn.api.ParamInitializer;
import org.deeplearning4j.nn.gradient.Gradient;
import org.deeplearning4j.nn.params.DefaultParamInitializer;
import org.deeplearning4j.optimize.Solver;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.ops.transforms.Transforms;


import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;

import java.lang.reflect.Constructor;
import java.util.Map;

/**
 * A layer with a bias and activation function
 * @author Adam Gibson
 */
public abstract class BaseLayer implements Layer {

    protected INDArray input;
    protected Map<String,INDArray> params;
    protected NeuralNetConfiguration conf;
    protected INDArray dropoutMask;
    protected ParamInitializer paramInitializer;


    public BaseLayer(NeuralNetConfiguration conf, INDArray input) {
        this.input = input;
        this.conf = conf;


    }


    @Override
    public void setConf(NeuralNetConfiguration conf) {
        this.conf = conf;
    }

    @Override
    public void setParam(String key, INDArray val) {
        params.put(key,val);
    }

    /**
     * Returns the parameters of the neural network
     *
     * @return the parameters of the neural network
     */
    @Override
    public INDArray params() {
        return Nd4j.toFlattened(params.values());
    }

    @Override
    public void initParams() {
        paramInitializer.init(paramTable(),conf());

    }

    @Override
    public Map<String, INDArray> paramTable() {
        return params;
    }

    @Override
    public void setParamTable(Map<String, INDArray> paramTable) {
        this.params = paramTable;
    }

    @Override
    public INDArray getParam(String param) {
        return params.get(param);
    }

    /**
     * Classify input
     * @param x the input (can either be a matrix or vector)
     * If it's a matrix, each row is considered an example
     * and associated rows are classified accordingly.
     * Each row will be the likelihood of a label given that example
     * @return a probability distribution for each row
     */
    @Override
    public  INDArray preOutput(INDArray x) {
        if(x == null)
            throw new IllegalArgumentException("No null input allowed");

        this.input = x;
        INDArray b = getParam(DefaultParamInitializer.BIAS_KEY);
        INDArray W = getParam(DefaultParamInitializer.WEIGHT_KEY);


        INDArray ret = this.input.mmul(W);
        if(ret.columns() != b.columns())
            throw new IllegalStateException("This is weird");
        if(conf.isConcatBiases())
            ret = Nd4j.hstack(ret,b);
        else
            ret.addiRowVector(b);
        return ret;


    }


    @Override
    public int batchSize() {
        return input.rows();
    }

    @Override
    public  INDArray activate() {
        INDArray b = getParam(DefaultParamInitializer.BIAS_KEY);
        INDArray W = getParam(DefaultParamInitializer.WEIGHT_KEY);


        INDArray activation =  conf.getActivationFunction().apply(getInput().mmul(W).addiRowVector(b));
        return activation;
    }

    @Override
    public  INDArray activate(INDArray input) {
        if(input != null)
            this.input = Transforms.stabilize(input, 1);
        return activate();
    }


    @Override
    public INDArray activationMean() {
        INDArray b = getParam(DefaultParamInitializer.BIAS_KEY);
        INDArray W = getParam(DefaultParamInitializer.WEIGHT_KEY);


        INDArray hbiasMean = getInput().mmul(W).addRowVector(b);
        return hbiasMean;
    }

    @Override
    public NeuralNetConfiguration conf() {
        return conf;
    }

    @Override
    public void setConfiguration(NeuralNetConfiguration conf) {
        this.conf = conf;
    }


    @Override
    public INDArray getInput() {
        return input;
    }

    @Override
    public void setInput(INDArray input) {
        this.input = input;
    }




    protected void applyDropOutIfNecessary(INDArray input) {
        if(conf.getDropOut() > 0) {
            INDArray mask = Nd4j.rand(input.rows(), input.columns());
            mask.gti(2);
            this.dropoutMask = Nd4j.rand(input.rows(), input.columns()).gt(conf.getDropOut());
        }

        else
            this.dropoutMask = Nd4j.ones(input.rows(), conf.getnOut());

        //actually apply drop out
        input.muli(dropoutMask);

    }

    /**
     * Averages the given logistic regression
     * from a mini batch in to this one
     * @param l the logistic regression to average in to this one
     * @param batchSize  the batch size
     */
    @Override
    public void merge(Layer l,int batchSize) {
          setParams(params().addi(l.params().divi(batchSize)));
    }


    @Override
    public Layer clone() {
        INDArray W = getParam(DefaultParamInitializer.WEIGHT_KEY);
        INDArray b = getParam(DefaultParamInitializer.BIAS_KEY);


        Layer layer = null;
        try {
            Constructor c = getClass().getConstructor(NeuralNetConfiguration.class,INDArray.class,INDArray.class,INDArray.class);
            layer = (Layer) c.newInstance(conf,W.dup(),b.dup(),input != null  ? input.dup() : null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return layer;

    }




    /**
     * The number of parameters for the model
     *
     * @return the number of parameters for the model
     */
    @Override
    public int numParams() {
        int ret = 0;
        for(INDArray val : params.values())
            ret += val.length();
        return ret;
    }

    @Override
    public void fit(INDArray input) {
        if(input != null)
            this.input = input;
        Solver solver = new Solver.Builder()
                .model(this).configure(conf()).listeners(conf.getListeners())
                .build();

        solver.optimize();
    }


    @Override
    public Pair<Gradient, Double> gradientAndScore() {
        return new Pair<>(getGradient(),score());
    }


    @Override
    public Layer transpose() {
        INDArray W = getParam(DefaultParamInitializer.WEIGHT_KEY);
        INDArray b = getParam(DefaultParamInitializer.BIAS_KEY);


        Layer layer = null;
        try {
            Constructor c = getClass().getConstructor(NeuralNetConfiguration.class,INDArray.class,INDArray.class,INDArray.class);
            NeuralNetConfiguration clone = conf.clone();
            int nIn = clone.getnOut(),nOut = clone.getnIn();
            clone.setnIn(nIn);
            clone.setnOut(nOut);
            layer = (Layer) c.newInstance(conf,W.transpose().dup(),b.transpose().dup(),input != null  ? input.transpose().dup() : null);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return layer;
    }

}