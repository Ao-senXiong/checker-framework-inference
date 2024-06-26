package checkers.inference.test;

import org.checkerframework.framework.test.CheckerFrameworkPerFileTest;
import org.checkerframework.framework.test.TestUtilities;
import org.checkerframework.javacutil.SystemUtil;
import org.junit.Test;
import org.plumelib.util.IPair;
import org.plumelib.util.SystemPlume;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.processing.AbstractProcessor;

public abstract class CFInferenceTest extends CheckerFrameworkPerFileTest {

    public static final boolean isAtMost7Jvm;

    static {
        isAtMost7Jvm = SystemUtil.jreVersion <= 7;
    }

    public CFInferenceTest(
            File testFile,
            Class<? extends AbstractProcessor> checker,
            String testDir,
            String... checkerOptions) {
        super(testFile, checker, testDir, checkerOptions);
    }

    public boolean useHacks() {
        return SystemPlume.getBooleanSystemProperty("use.hacks");
    }

    public abstract IPair<String, List<String>> getSolverNameAndOptions();

    public List<String> getAdditionalInferenceOptions() {
        return new ArrayList<String>();
    }

    public String getPathToAfuScripts() {
        return System.getProperty("path.afu.scripts");
    }

    public String getPathToInferenceScript() {
        return System.getProperty("path.inference.script");
    }

    @Override
    @Test
    public void run() {
        boolean shouldEmitDebugInfo = TestUtilities.getShouldEmitDebugInfo();
        IPair<String, List<String>> solverArgs = getSolverNameAndOptions();

        final File testDataDir = new File("testdata");

        InferenceTestConfiguration config =
                InferenceTestConfigurationBuilder.buildDefaultConfiguration(
                        testDir,
                        testFile,
                        testDataDir,
                        checker,
                        checkerOptions,
                        getAdditionalInferenceOptions(),
                        solverArgs.first,
                        solverArgs.second,
                        useHacks(),
                        shouldEmitDebugInfo,
                        getPathToAfuScripts(),
                        getPathToInferenceScript());

        InferenceTestResult testResult = new InferenceTestExecutor().runTest(config);
        InferenceTestUtilities.assertResultsAreValid(testResult);
    }
}
