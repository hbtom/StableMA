package code.sma.recmmd.ensemble;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.junit.Test;

import code.sma.core.AbstractMatrix;
import code.sma.main.Configures;
import code.sma.thread.SimpleLearner;
import code.sma.thread.SimpleTaskMsgDispatcherImpl;
import code.sma.thread.TaskMsgDispatcher;
import code.sma.util.ConfigureUtil;
import code.sma.util.ExceptionUtil;
import code.sma.util.LoggerDefineConstant;
import code.sma.util.LoggerUtil;
import code.sma.util.MatrixIOUtil;
import junit.framework.TestCase;

/**
 * 
 * @author Chao.Chen
 * @version $Id: MultTskREC.java, v 0.1 2017年2月28日 下午4:23:17 Chao.Chen Exp $
 */
public class MultTskRECTest extends TestCase {
    /** the logger instance*/
    protected final static Logger logger = Logger.getLogger(LoggerDefineConstant.SERVICE_NORMAL);

    /** 
     * @see junit.framework.TestCase#setUp()
     */
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testAlg() throws IOException {
        Configures conf = ConfigureUtil.read("src/main/resources/samples/MTREC.properties");
        String[] rootDirs = conf.getProperty("ROOT_DIRs").split("\\,");

        for (String rootDir : rootDirs) {
            LoggerUtil.info(logger, "1. loading " + rootDir);
            conf.setProperty("ROOT_DIR", rootDir);
            String trainFile = rootDir + "trainingset";
            String testFile = rootDir + "testingset";

            Configures new_conf = new Configures(conf);
            String dconfFile = rootDir + "dConfig.properties";
            ConfigureUtil.addConfig(new_conf, dconfFile);

            String algName = new_conf.getProperty("ALG_NAME");
            LoggerUtil.info(logger, "2. running " + algName);

            TaskMsgDispatcher stkmImpl = new SimpleTaskMsgDispatcherImpl(new_conf);
            int threadNum = new_conf.getInteger("THREAD_NUMBER_VALUE");

            AbstractMatrix train = MatrixIOUtil.loadCSRMatrix(trainFile,
                new_conf.getInteger("TRAIN_ROW_NUM_VALUE"),
                new_conf.getInteger("TRAIN_VAL_NUM_VALUE"));
            AbstractMatrix test = MatrixIOUtil.loadCSRMatrix(testFile,
                new_conf.getInteger("TEST_ROW_NUM_VALUE"),
                new_conf.getInteger("TEST_VAL_NUM_VALUE"));

            try {
                ExecutorService exec = Executors.newCachedThreadPool();
                for (int t = 0; t < threadNum; t++) {
                    exec.execute(new SimpleLearner(stkmImpl, train, test));
                }
                exec.shutdown();
                exec.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
            } catch (InterruptedException e) {
                ExceptionUtil.caught(e, "Stand-alone model Thead!");
            }
        }
    }

    /** 
     * @see junit.framework.TestCase#tearDown()
     */
    protected void tearDown() throws Exception {

    }

}
