package com.splunk.splunkjenkins.utils;

import com.splunk.splunkjenkins.model.AbstractTestResultAdapter;
import com.splunk.splunkjenkins.model.EmptyTestCaseGroup;
import com.splunk.splunkjenkins.model.JunitTestCaseGroup;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.Publisher;
import hudson.tasks.test.TestResult;
import hudson.tasks.test.AbstractTestResultAction;
import hudson.util.DescribableList;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.splunk.splunkjenkins.utils.LogEventHelper.hasPublisherName;

public class TestCaseResultUtils {
    /**
     * split test result into groups, each contains maximum pageSize testcases
     *
     * @param results  Test Results
     * @param pageSize how many test cases to hold in one page
     * @param <T>      generic sub types of TestResult
     * @return A list of JunitTestCaseGroup
     */
    public static <T extends TestResult> List<JunitTestCaseGroup> split(@Nonnull List<T> results, int pageSize) {
        List<JunitTestCaseGroup> testCasesCollect = new ArrayList<>();
        if (results.isEmpty()) {
            return testCasesCollect;
        }
        JunitTestCaseGroup group = new JunitTestCaseGroup();
        testCasesCollect.add(group);
        for (T testCase : results) {
            group.add(testCase);
            if (group.getTotal() >= pageSize) {
                group = new JunitTestCaseGroup();
                testCasesCollect.add(group);
            }
        }
        return testCasesCollect;
    }

    /**
     * @param resultAction Junit Test Result Action
     * @param pageSize     how many test cases to hold in one page
     * @return A list of JunitTestCaseGroup
     */
    public static List<JunitTestCaseGroup> splitRaw(AbstractTestResultAction resultAction, int pageSize) {
        List<JunitTestCaseGroup> testCasesCollect = new ArrayList<>();
        JunitTestCaseGroup group = new JunitTestCaseGroup();
        testCasesCollect.add(group);
        List<TestResult> results = new ArrayList<>();
        results.addAll(resultAction.getFailedTests());
        results.addAll(resultAction.getSkippedTests());
        results.addAll(resultAction.getPassedTests());
        for (TestResult testCase : results) {
            group.add(testCase);
            if (group.getTotal() > pageSize) {
                group = new JunitTestCaseGroup();
                testCasesCollect.add(group);
            }
        }
        return testCasesCollect;
    }

    /**
     * Get the Junit report  from build
     * Extract from either TestResultAction or AggregatedTestResultAction
     *
     * @param build    Jenkins build
     * @param pageSize how many test cases to hold in one page
     * @return A list of JunitTestCaseGroup
     */
    @Nonnull
    public static List<JunitTestCaseGroup> getBuildReport(Run build, int pageSize) {
        List<JunitTestCaseGroup> junitReports = new ArrayList<>();
        if (build == null) {
            return junitReports;
        }
        List<TestResult> results = AbstractTestResultAdapter.getTestResult(build);
        junitReports = split(results, pageSize);
        if (junitReports.isEmpty()) {
            //last resort, try AbstractTestResultAction
            AbstractTestResultAction abstractTestResultAction = build.getAction(AbstractTestResultAction.class);
            if (abstractTestResultAction != null) {
                junitReports = splitRaw(abstractTestResultAction, pageSize);
            }
        }
        if (junitReports.isEmpty()) {
            EmptyTestCaseGroup emptyReport = new EmptyTestCaseGroup();
            if (build.getResult() != Result.SUCCESS &&
                    (hasPublisherName("junit.JUnitResultArchiver", build) || hasPublisherName("testng.Publisher", build))) {
                emptyReport.setWarning(true);
            }
            junitReports.add(emptyReport);
        }
        return junitReports;
    }

    /**
     * @param build Jenkins build
     * @return summary of failures,passes,skips, total and duration
     */
    public static Map<String, Object> getSummary(Run build) {
        List<JunitTestCaseGroup> results = getBuildReport(build, Integer.MAX_VALUE);
        Map<String, Object> summary = new HashMap();
        if (results.isEmpty()) {
            return summary;
        }
        JunitTestCaseGroup testResult = results.get(0);
        summary.put("failures", testResult.getFailures());
        summary.put("passes", testResult.getPasses());
        summary.put("skips", testResult.getSkips());
        summary.put("total", testResult.getTotal());
        summary.put("duration", testResult.getDuration());
        return summary;
    }
}
