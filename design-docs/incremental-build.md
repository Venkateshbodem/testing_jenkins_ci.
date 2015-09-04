# Incremental Build Improvements

This spec defines some improvements to improve incremental build and task up-to-date checks

# Establishing Baseline

## Story: Add 'profiler' (YourKit) hook to performance test harness

- Make it easier for us to collect profiling data and share profiling setup
- Gradle dev adds `YJP_HOME` (Yourkit home directory path) or `YJP_AGENT_PATH` (Yourkit agent library file path) environment variable.
- Gradle dev writes a performance test that extends AbstractCrossBuildPerformanceTest or AbstractCrossVersionPerformanceTest.
- Gradle dev enables YJP by passing `-Porg.gradle.performance.use_yourkit` project property in running the performance test.
  - example use: `./gradlew performance:performanceTest -Porg.gradle.performance.use_yourkit -D:performance:performanceTest.single=NativePreCompiledHeaderPerformanceTest`
- Yourkit agent options are loaded from `~/.gradle/yourkit.properties` by default.
  - Yourkit supports these startup options: https://www.yourkit.com/docs/java/help/startup_options.jsp .
- Yourkit profiling snapshot data get saved to `~/Snapshots` by default. The file name contains the test project name and display name from the performance test.

### Test coverage

- TBD, whatever makes sense (this should touch test infrastructure only)

### Open Issues

- Skip warm-up or have explicit opt-in for warm up profiling?
  - Conditionally enabling the profiler in the code by using Yourkit API.
- For cross-version tests, skip all versions except the current?
- Do we care about conditionally profiling the CLI and daemon processes (such that the profiling options should be configurable for each)?

## Story: Compare tasks using IncrementalTaskInputs with "regular" tasks

- Vary number of inputs and outputs
- If difference between calculating up-to-date checks is identical or within an order of magnitude, remove this as a variable in future plans.
- Collect numbers for a report

### Test coverage

- This does not need to be automated
- Compare time to perform up-to-date checks for otherwise identical tasks at
   - 1, 100, 1000 and 10000 inputs
   - 1, 100, 1000 and 10000 outputs
   - 16 tests for each case (32 total)

## Story: Compare "one change" and "no change" cases

- Vary number of inputs and outputs
- Compare no input or output change case with one input or output change case
- If difference between calculating up-to-date checks is identical or within an order of magnitude, remove this as a variable for now and focus on 'no change' cases.
- Future stories may address the "ripple" effect of a single input invalidating multiple tasks in a chain.
- Collect numbers for a report

### Test coverage

- This does not need to be automated
- Collect time to perform up-to-date checks at
   - 1, 100, 1000 and 10000 inputs
   - 1, 100, 1000 and 10000 outputs
   - 16 tests for each case (32 total)

## Story: Find breaking point for input and output sizes

- Vary number of inputs and outputs
- Vary sizes of inputs and outputs
- Breaking point is the point where Gradle fails due to memory (reported issue) or takes a "long" time (>60 seconds for a single task).

### Test coverage

- This does not need to be automated
- Collect time to perform up-to-date checks at
   - 10k, 50k, 100k, 500k, 1m inputs (smaller if impractical)
   - 10k, 50k, 100k, 500k, 1m outputs (smaller if impractical)
   - 16 tests
- Collect time to perform up-to-date checks for 100 inputs at
   - 1MB, 10MB, 50MB, 100MB inputs (smaller if impractical)

## Story: Update performance generator to create representative Java project

See discussion about parameters.  Uses java-lang/jvm-component software model plugins.

### Test coverage

- Performance test that runs against 2.6 and latest release

## Story: Update performance generator to create representative C/C++ project

See discussion about parameters.  Uses cpp software model plugins.

### Test coverage

- Performance test that runs against 2.6 and latest release

## Story: TBD

TBD

### Test coverage

- TBD

## Open Issues

- Check behavior of Prebuilt Libraries with large number of headers (https://discuss.gradle.org/t/native-performance-issue-with-no-op-builds-with-boost-as-a-dependency)
- See incremental-build-old.md for other ideas and specific issues.
