package code.sma.recmmd.standalone;

import org.apache.log4j.Logger;

import code.sma.core.impl.DenseMatrix;
import code.sma.core.impl.DenseVector;
import code.sma.core.impl.Tuples;
import code.sma.recmmd.Loss;
import code.sma.recmmd.RecConfigEnv;
import code.sma.recmmd.Recommender;
import code.sma.recmmd.Regularizer;
import code.sma.util.EvaluationMetrics;
import code.sma.util.LoggerDefineConstant;
import code.sma.util.LoggerUtil;
import code.sma.util.StringUtil;

/**
 * This is an abstract class implementing four matrix-factorization-based methods
 * including Regularized SVD, NMF, PMF, and Bayesian PMF.
 * 
 * @author Joonseok Lee
 * @since 2012. 4. 20
 * @version 1.1
 */
public abstract class MFRecommender extends Recommender {
    /** SerialVersionNum */
    protected static final long             serialVersionUID = 1L;

    /** The number of features. */
    public int                              featureCount;
    /** Learning rate parameter. */
    public double                           learningRate;
    /** Regularization factor parameter. */
    public double                           regularizer;
    /** Momentum parameter. */
    public double                           momentum;
    /** Maximum number of iteration. */
    public int                              maxIter;
    /** The best RMSE in test*/
    public double                           bestRMSE         = Double.MAX_VALUE;

    /** Indicator whether to show progress of iteration. */
    public boolean                          showProgress;
    /** Offset to rating estimation. Usually this is the average of ratings. */
    protected double                        offset;

    /** User profile in low-rank matrix form. */
    public DenseMatrix                      userDenseFeatures;
    /** Item profile in low-rank matrix form. */
    public DenseMatrix                      itemDenseFeatures;

    /** indices involved in training */
    public transient int[]                  trainInvlvIndces;
    /** indices involved in testing */
    public transient int[]                  testInvlvIndces;
    /** the loss funciton to measure the distance between real value and approximated value*/
    protected Loss                          lossFunction;
    /** regularizer used to control the complexity of the method*/
    protected Regularizer                   regType;

    /** user average rating*/
    protected DenseVector                   avgUser;
    /** item average rating*/
    protected DenseVector                   avgItem;

    /** logger */
    protected final static transient Logger runningLogger    = Logger
        .getLogger(LoggerDefineConstant.SERVICE_CORE);
    protected final static transient Logger resultLogger     = Logger
        .getLogger(LoggerDefineConstant.SERVICE_NORMAL);

    /*========================================
     * Constructors
     *========================================*/
    public MFRecommender(RecConfigEnv rce) {
        commonParse(rce);
    }

    public MFRecommender(RecConfigEnv rce, DenseMatrix userDenseFeatures,
                         DenseMatrix itemDenseFeatures) {
        commonParse(rce);
        this.userDenseFeatures = userDenseFeatures;
        this.itemDenseFeatures = itemDenseFeatures;
    }

    public MFRecommender(RecConfigEnv rce, int[] trainInvlvIndces, int[] testInvlvIndces) {
        commonParse(rce);

        this.trainInvlvIndces = trainInvlvIndces;
        this.testInvlvIndces = testInvlvIndces;
    }

    private void commonParse(RecConfigEnv rce) {
        this.featureCount = ((Float) rce.get("FEATURE_COUNT_VALUE")).intValue();
        this.learningRate = ((Float) rce.get("LEARNING_RATE_VALUE")).doubleValue();
        this.regularizer = ((Float) rce.get("REGULAIZED_VALUE")).doubleValue();
        this.maxIter = ((Float) rce.get("MAX_ITERATION_VALUE")).intValue();

        this.userCount = ((Float) rce.get("USER_COUNT_VALUE")).intValue();
        this.itemCount = ((Float) rce.get("ITEM_COUNT_VALUE")).intValue();
        this.maxValue = ((Float) rce.get("MAX_RATING_VALUE")).doubleValue();
        this.minValue = ((Float) rce.get("MIN_RATING_VALUE")).doubleValue();
        this.showProgress = (Boolean) rce.get("VERBOSE_BOOLEAN");

        String lsfnctn = (String) rce.get("LOSS_FUNCTION");
        if (StringUtil.isNotBlank(lsfnctn)) {
            this.lossFunction = Loss.valueOf(lsfnctn);
        } else {
            this.lossFunction = Loss.LOSS_RMSE;
        }

        String rtype = (String) rce.get("REG_TYPE");
        if (StringUtil.isNotBlank(rtype)) {
            this.regType = Regularizer.valueOf(rtype);
        } else {
            this.regType = Regularizer.L2;
        }
    }

    /*========================================
     * Model Builder
     *========================================*/
    /**
     * @see edu.tongji.ml.Recommender#buildModel(edu.tongji.data.Tuples, edu.tongji.data.Tuples)
     */
    @Override
    public void buildModel(Tuples train, Tuples test) {
        LoggerUtil.info(runningLogger, String.format("Param: FC:%d\tLR:%.5f\tR:%.5f", featureCount,
            learningRate, regularizer));
        userDenseFeatures = new DenseMatrix(userCount, featureCount);
        itemDenseFeatures = new DenseMatrix(itemCount, featureCount);
    }

    /**
     * @see code.sma.recmmd.Recommender#buildloclModel(code.sma.core.impl.Tuples, code.sma.core.impl.Tuples)
     */
    @Override
    public void buildloclModel(Tuples train, Tuples test) {
        LoggerUtil.info(runningLogger,
            String.format("Param: FC:%d,LR:%.7f,R:%.7f", featureCount, learningRate, regularizer));
        userDenseFeatures = new DenseMatrix(userCount, featureCount);
        itemDenseFeatures = new DenseMatrix(itemCount, featureCount);
    }

    /*========================================
     * Record Logs & Dynamic Stopper
     *========================================*/
    /**
     * Record Logs & Dynamic Stopper
     * 
     * @param round         the current round
     * @param tMatrix       test matrix
     * @param currErr       the current training error
     * @return              true to stop, false to continue
     */
    protected boolean recordLoggerAndDynamicStop(int round, Tuples tMatrix, double currErr) {
        if (showProgress && (round % 5 == 0) && tMatrix != null) {
            EvaluationMetrics metric = evaluate(tMatrix);
            LoggerUtil.info(runningLogger,
                String.format("%d\t%.6f [%s]", round, currErr, metric.printOneLine()));
            if (bestRMSE >= metric.getRMSE()) {
                bestRMSE = metric.getRMSE();
            } else {
                return true;
            }
        } else {
            LoggerUtil.info(runningLogger, String.format("%d\t%.6f", round, currErr));
        }

        return false;
    }

    /*========================================
     * Prediction
     *========================================*/

    /**
     * @see code.sma.recmmd.Recommender#evaluate(code.sma.core.impl.Tuples)
     */
    @Override
    public EvaluationMetrics evaluate(Tuples testMatrix) {
        return new EvaluationMetrics(this, testMatrix);
    }

    /**
     * @see edu.tongji.ml.Recommender#predict(int, int)
     */
    @Override
    public double predict(int u, int i) {
        // compute the prediction by using inner product 
        if (avgUser != null && avgItem != null) {
            this.offset = (avgUser.getValue(u) + avgItem.getValue(i)) / 2.0;
        }

        double prediction = this.offset;
        if (userDenseFeatures != null && itemDenseFeatures != null) {
            prediction += userDenseFeatures.innerProduct(u, i, itemDenseFeatures, false);
        } else {
            throw new RuntimeException("features were not initialized.");
        }

        // normalize the prediction
        if (prediction > maxValue) {
            return maxValue;
        } else if (prediction < minValue) {
            return minValue;
        } else {
            return prediction;
        }
    }

}