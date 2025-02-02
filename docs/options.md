# Command-Line Options

_EvoMaster_ has several options that can be configured. 
Those can be set on the command-line when _EvoMaster_ is started.

There are 3 types of options:

* __Important__: those are the main options that most users will need to set
                and know about.

* __Internal__: these are low-level tuning options, which most users do not need
                to modify. These were mainly introduced when experimenting with 
                different configurations to maximize the performance of _EvoMaster_.
                
* __Experimental__: these are work-in-progress options, for features still under development
                    and testing.        
         

 The list of available options can also be displayed by using `--help`, e.g.:

 `java -jar evomaster.jar --help`
  
  Options might also have *constraints*, e.g., a numeric value within a defined range,
  or a string being an URL.
  In some cases, strings might only be chosen within a specific set of possible values (i.e., an Enum).
  If any constraint is not satisfied, _EvoMaster_ will fail with an error message.
  
  When used, all options need to be prefixed with a `--`, e.g., `--maxTime`.
  
## Important Command-Line Options

|Options|Description|
|---|---|
|`maxTime`| __String__. Maximum amount of time allowed for the search.  The time is expressed with a string where hours (`h`), minutes (`m`) and seconds (`s`) can be specified, e.g., `1h10m120s` and `72m` are both valid and equivalent. Each component (i.e., `h`, `m` and `s`) is optional, but at least one must be specified.  In other words, if you need to run the search for just `30` seconds, you can write `30s`  instead of `0h0m30s`. **The more time is allowed, the better results one can expect**. But then of course the test generation will take longer. For how long should _EvoMaster_ be left run? The default 1 _minute_ is just for demonstration. __We recommend to run it between 1 and 24 hours__, depending on the size and complexity  of the tested application. *Constraints*: `regex (\s*)((?=(\S+))(\d+h)?(\d+m)?(\d+s)?)(\s*)`. *Default value*: `60s`.|
|`outputFilePrefix`| __String__. The name prefix of generated file(s) with the test cases, without file type extension. In JVM languages, if the name contains '.', folders will be created to represent the given package structure. Also, in JVM languages, should not use '-' in the file name, as not valid symbol for class identifiers. This prefix be combined with the outputFileSuffix to combined the final name. As EvoMaster can split the generated tests among different files, each will get a label, and the names will be in the form prefix+label+suffix. *Constraints*: `regex [-a-zA-Z$_][-0-9a-zA-Z$_]*(.[-a-zA-Z$_][-0-9a-zA-Z$_]*)*`. *Default value*: `EvoMaster`.|
|`outputFileSuffix`| __String__. The name suffix for the generated file(s), to be added before the file type extension. As EvoMaster can split the generated tests among different files, each will get a label, and the names will be in the form prefix+label+suffix. *Constraints*: `regex [-a-zA-Z$_][-0-9a-zA-Z$_]*(.[-a-zA-Z$_][-0-9a-zA-Z$_]*)*`. *Default value*: `Test`.|
|`outputFolder`| __String__. The path directory of where the generated test classes should be saved to. *Default value*: `src/em`.|
|`outputFormat`| __Enum__. Specify in which format the tests should be outputted. If left on `DEFAULT`, then the value specified in the _EvoMaster Driver_ will be used. But a different value must be chosen if doing Black-Box testing. *Valid values*: `DEFAULT, JAVA_JUNIT_5, JAVA_JUNIT_4, KOTLIN_JUNIT_4, KOTLIN_JUNIT_5, JS_JEST, CSHARP_XUNIT`. *Default value*: `DEFAULT`.|
|`testTimeout`| __Int__. Enforce timeout (in seconds) in the generated tests. This feature might not be supported in all frameworks. If 0 or negative, the timeout is not applied. *Default value*: `60`.|
|`blackBox`| __Boolean__. Use EvoMaster in black-box mode. This does not require an EvoMaster Driver up and running. However, you will need to provide further option to specify how to connect to the SUT. *Default value*: `false`.|
|`bbSwaggerUrl`| __String__. When in black-box mode for REST APIs, specify the URL of where the OpenAPI/Swagger schema can be downloaded from. If the schema is on the local machine, you can use a URL starting with 'file://'. *Constraints*: `URL`. *Default value*: `""`.|
|`bbTargetUrl`| __String__. When in black-box mode, specify the URL of where the SUT can be reached, e.g., http://localhost:8080 . In REST, if this is missing, the URL will be inferred from OpenAPI/Swagger schema. In GraphQL, this must point to the entry point of the API, e.g., http://localhost:8080/graphql . *Constraints*: `URL`. *Default value*: `""`.|
|`ratePerMinute`| __Int__. Rate limiter, of how many actions to do per minute. For example, when making HTTP calls towards an external service, might want to limit the number of calls to avoid bombarding such service (which could end up becoming equivalent to a DoS attack). A value of zero or negative means that no limiter is applied. This is needed only for black-box testing of remote services. *Default value*: `0`.|
|`header0`| __String__. In black-box testing, we still need to deal with authentication of the HTTP requests. With this parameter it is possible to specify a HTTP header that is going to be added to most requests. This should be provided in the form _name:value_. If more than 1 header is needed, use as well the other options _header1_ and _header2_. *Constraints*: `regex (.+:.+)\|(^$)`. *Default value*: `""`.|
|`header1`| __String__. See documentation of _header0_. *Constraints*: `regex (.+:.+)\|(^$)`. *Default value*: `""`.|
|`header2`| __String__. See documentation of _header0_. *Constraints*: `regex (.+:.+)\|(^$)`. *Default value*: `""`.|
|`jsControllerPath`| __String__. When generating tests in JavaScript, there is the need to know where the driver is located in respect to the generated tests. *Default value*: `./app-driver.js`.|

## Internal Command-Line Options

|Options|Description|
|---|---|
|`adaptiveGeneSelectionMethod`| __Enum__. Specify a strategy to select genes for mutation adaptively. *Valid values*: `NONE, AWAY_NOIMPACT, APPROACH_IMPACT, APPROACH_LATEST_IMPACT, APPROACH_LATEST_IMPROVEMENT, BALANCE_IMPACT_NOIMPACT, BALANCE_IMPACT_NOIMPACT_WITH_E, ALL_FIXED_RAND`. *Default value*: `APPROACH_IMPACT`.|
|`addPreDefinedTests`| __Boolean__. Add predefined tests at the end of the search. An example is a test to fetch the schema of RESTful APIs. *Default value*: `true`.|
|`algorithm`| __Enum__. The algorithm used to generate test cases. *Valid values*: `MIO, RANDOM, WTS, MOSA`. *Default value*: `MIO`.|
|`appendToStatisticsFile`| __Boolean__. Whether should add to an existing statistics file, instead of replacing it. *Default value*: `false`.|
|`archiveGeneMutation`| __Enum__. Whether to enable archive-based gene mutation. *Valid values*: `NONE, SPECIFIED, SPECIFIED_WITH_TARGETS, SPECIFIED_WITH_SPECIFIC_TARGETS, SPECIFIED_WITH_TARGETS_DIRECTION, SPECIFIED_WITH_SPECIFIC_TARGETS_DIRECTION, ADAPTIVE`. *Default value*: `SPECIFIED_WITH_SPECIFIC_TARGETS`.|
|`archiveTargetLimit`| __Int__. Limit of number of individuals per target to keep in the archive. *Constraints*: `min=1.0`. *Default value*: `10`.|
|`avoidNonDeterministicLogs`| __Boolean__. At times, we need to run EvoMaster with printed logs that are deterministic. For example, this means avoiding printing out time-stamps. *Default value*: `false`.|
|`baseTaintAnalysisProbability`| __Double__. Probability to use input tracking (i.e., a simple base form of taint-analysis) to determine how inputs are used in the SUT. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.9`.|
|`bbExperiments`| __Boolean__. Only used when running experiments for black-box mode, where an EvoMaster Driver would be present, and can reset state after each experiment. *Default value*: `false`.|
|`bloatControlForSecondaryObjective`| __Boolean__. Whether secondary objectives are less important than test bloat control. *Default value*: `false`.|
|`coveredTargetFile`| __String__. Specify a file which saves covered targets info regarding generated test suite. *Default value*: `coveredTargets.txt`.|
|`createTests`| __Boolean__. Specify if test classes should be created as output of the tool. Usually, you would put it to 'false' only when debugging EvoMaster itself. *Default value*: `true`.|
|`customNaming`| __Boolean__. Enable custom naming and sorting criteria. *Default value*: `true`.|
|`d`| __Double__. When weight-based mutation rate is enabled, specify a percentage of calculating mutation rate based on a number of candidate genes to mutate. For instance, d = 1.0 means that the mutation rate fully depends on a number of candidate genes to mutate, and d = 0.0 means that the mutation rate fully depends on weights of candidates genes to mutate. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.8`.|
|`doesApplyNameMatching`| __Boolean__. Whether to apply text/name analysis to derive relationships between name entities, e.g., a resource identifier with a name of table. *Default value*: `true`.|
|`e_u1f984`| __Boolean__. QWN0aXZhdGUgdGhlIFVuaWNvcm4gTW9kZQ==. *Default value*: `false`.|
|`enableBasicAssertions`| __Boolean__. Generate basic assertions. Basic assertions (comparing the returned object to itself) are added to the code. NOTE: this should not cause any tests to fail. *Default value*: `true`.|
|`enableTrackEvaluatedIndividual`| __Boolean__. Whether to enable tracking the history of modifications of the individuals with its fitness values (i.e., evaluated individual) during the search. Note that we enforced that set enableTrackIndividual false when enableTrackEvaluatedIndividual is true since information of individual is part of evaluated individual. *Default value*: `true`.|
|`enableWeightBasedMutationRateSelectionForGene`| __Boolean__. Specify whether to enable weight-based mutation selection for selecting genes to mutate for a gene. *Default value*: `true`.|
|`endNumberOfMutations`| __Int__. Number of applied mutations on sampled individuals, by the end of the search. *Constraints*: `min=0.0`. *Default value*: `10`.|
|`endpointFocus`| __String__. Only for debugging. Concentrate search on only one single REST endpoint. *Default value*: `null`.|
|`executiveSummary`| __Boolean__. Generate an executive summary, containing an example of each category of potential fault found.NOTE: This option is only meaningful when used in conjuction with clustering. This is achieved by turning the option --testSuiteSplitType to CLUSTER. *Default value*: `true`.|
|`expandRestIndividuals`| __Boolean__. Enable to expand the genotype of REST individuals based on runtime information missing from Swagger. *Default value*: `true`.|
|`expectationsActive`| __Boolean__. Enable Expectation Generation. If enabled, expectations will be generated. A variable called expectationsMasterSwitch is added to the test suite, with a default value of false. If set to true, an expectation that fails will cause the test case containing it to fail. *Default value*: `true`.|
|`exportCoveredTarget`| __Boolean__. Specify whether to export covered targets info. *Default value*: `false`.|
|`extraHeuristicsFile`| __String__. Where the extra heuristics file (if any) is going to be written (in CSV format). *Default value*: `extra_heuristics.csv`.|
|`extractSqlExecutionInfo`| __Boolean__. Enable extracting SQL execution info. *Default value*: `true`.|
|`feedbackDirectedSampling`| __Enum__. Specify whether when we sample from archive we do look at the most promising targets for which we have had a recent improvement. *Valid values*: `NONE, LAST, FOCUSED_QUICKEST`. *Default value*: `LAST`.|
|`focusedSearchActivationTime`| __Double__. The percentage of passed search before starting a more focused, less exploratory one. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.5`.|
|`geneMutationStrategy`| __Enum__. Strategy used to define the mutation probability. *Valid values*: `ONE_OVER_N, ONE_OVER_N_BIASED_SQL`. *Default value*: `ONE_OVER_N_BIASED_SQL`.|
|`geneWeightBasedOnImpactsBy`| __Enum__. Specify a strategy to calculate a weight of a gene based on impacts. *Valid values*: `SORT_COUNTER, SORT_RATIO, COUNTER, RATIO`. *Default value*: `RATIO`.|
|`generateSqlDataWithSearch`| __Boolean__. Enable EvoMaster to generate SQL data with direct accesses to the database. Use a search algorithm. *Default value*: `true`.|
|`heuristicsForSQL`| __Boolean__. Tracking of SQL commands to improve test generation. *Default value*: `true`.|
|`instrumentMR_BASE`| __Boolean__. Execute instrumentation for method replace with category BASE. Note: this applies only for languages in which instrumentation is applied at runtime, like Java/Kotlin on the JVM. *Default value*: `true`.|
|`instrumentMR_EXT_0`| __Boolean__. Execute instrumentation for method replace with category EXT_0. Note: this applies only for languages in which instrumentation is applied at runtime, like Java/Kotlin on the JVM. *Default value*: `true`.|
|`instrumentMR_SQL`| __Boolean__. Execute instrumentation for method replace with category SQL. Note: this applies only for languages in which instrumentation is applied at runtime, like Java/Kotlin on the JVM. *Default value*: `true`.|
|`killSwitch`| __Boolean__. Try to enforce the stopping of SUT business-level code. This is needed when TCP connections timeouts, to avoid thread executions from previous HTTP calls affecting the current one. *Default value*: `true`.|
|`labelForExperiments`| __String__. When running experiments and statistic files are generated, all configs are saved. So, this one can be used as extra label for classifying the experiment. *Default value*: `-`.|
|`maxActionEvaluations`| __Int__. Maximum number of action evaluations for the search. A fitness evaluation can be composed of 1 or more actions, like for example REST calls or SQL setups. The more actions are allowed, the better results one can expect. But then of course the test generation will take longer. Only applicable depending on the stopping criterion. *Constraints*: `min=1.0`. *Default value*: `1000`.|
|`maxLengthForStrings`| __Int__. The maximum length allowed for evolved strings. Without this limit, strings could in theory be billions of characters long. *Constraints*: `min=0.0, max=20000.0`. *Default value*: `200`.|
|`maxLengthForStringsAtSamplingTime`| __Int__. Maximum length when sampling a new random string. Such limit can be bypassed when a string is mutated. *Constraints*: `min=0.0`. *Default value*: `16`.|
|`maxResponseByteSize`| __Int__. Maximum size (in bytes) that EM handles response payloads in the HTTP responses. If larger than that, a response will not be stored internally in EM during the test generation. This is needed to avoid running out of memory. *Default value*: `1000000`.|
|`maxSearchSuiteSize`| __Int__. Define the maximum number of tests in a suite in the search algorithms that evolve whole suites, e.g. WTS. *Constraints*: `min=1.0`. *Default value*: `50`.|
|`maxSqlInitActionsPerMissingData`| __Int__. When generating SQL data, how many new rows (max) to generate for each specific SQL Select. *Constraints*: `min=1.0`. *Default value*: `5`.|
|`maxTestSize`| __Int__. Max number of 'actions' (e.g., RESTful calls or SQL commands) that can be done in a single test. *Constraints*: `min=1.0`. *Default value*: `10`.|
|`maxTimeInSeconds`| __Int__. Maximum number of seconds allowed for the search. The more time is allowed, the better results one can expect. But then of course the test generation will take longer. Only applicable depending on the stopping criterion. If this value is 0, the setting 'maxTime' will be used instead. *Constraints*: `min=0.0`. *Default value*: `0`.|
|`maxlengthOfHistoryForAGM`| __Int__. Specify a maximum length of history when applying archive-based gene mutation. *Default value*: `10`.|
|`minimumSizeControl`| __Int__. Specify minimum size when bloatControlForSecondaryObjective. *Constraints*: `min=0.0`. *Default value*: `2`.|
|`populationSize`| __Int__. Define the population size in the search algorithms that use populations (e.g., Genetic Algorithms, but not MIO). *Constraints*: `min=1.0`. *Default value*: `30`.|
|`probOfApplySQLActionToCreateResources`| __Double__. Specify a probability to apply SQL actions for preparing resources for REST Action. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.5`.|
|`probOfArchiveMutation`| __Double__. Specify a probability to enable archive-based mutation. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.5`.|
|`probOfEnablingResourceDependencyHeuristics`| __Double__. Specify whether to enable resource dependency heuristics, i.e, probOfEnablingResourceDependencyHeuristics > 0.0. Note that the option is available to be enabled only if resource-based smart sampling is enable. This option has an effect on sampling multiple resources and mutating a structure of an individual. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.95`.|
|`probOfRandomSampling`| __Double__. Probability of sampling a new individual at random. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.5`.|
|`probOfSmartSampling`| __Double__. When sampling new test cases to evaluate, probability of using some smart strategy instead of plain random. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.95`.|
|`problemType`| __Enum__. The type of SUT we want to generate tests for, e.g., a RESTful API. If left to DEFAULT, the type will be inferred from the EM Driver. However, in case of ambiguities (e.g., the driver specifies more than one type), then this field must be set with a specific type. This is also the case for Black-Box testing where there is no EM Driver. In this latter case, the system defaults to handle REST APIs. *Valid values*: `DEFAULT, REST`. *Experimental values*: `GRAPHQL, RPC, WEB`. *Default value*: `DEFAULT`.|
|`resourceSampleStrategy`| __Enum__. Specify whether to enable resource-based strategy to sample an individual during search. Note that resource-based sampling is only applicable for REST problem with MIO algorithm. *Valid values*: `NONE, Customized, EqualProbability, Actions, TimeBudgets, Archive, ConArchive`. *Default value*: `ConArchive`.|
|`secondaryObjectiveStrategy`| __Enum__. Strategy used to handle the extra heuristics in the secondary objectives. *Valid values*: `AVG_DISTANCE, AVG_DISTANCE_SAME_N_ACTIONS, BEST_MIN`. *Default value*: `AVG_DISTANCE_SAME_N_ACTIONS`.|
|`seed`| __Long__. The seed for the random generator used during the search. A negative value means the CPU clock time will be rather used as seed. *Default value*: `-1`.|
|`showProgress`| __Boolean__. Whether to print how much search done so far. *Default value*: `true`.|
|`skipFailureSQLInTestFile`| __Boolean__. Whether to skip failed SQL commands in the generated test files. *Default value*: `true`.|
|`snapshotInterval`| __Double__. If positive, check how often, in percentage % of the budget, to collect statistics snapshots. For example, every 5% of the time. *Constraints*: `max=50.0`. *Default value*: `-1.0`.|
|`snapshotStatisticsFile`| __String__. Where the snapshot file (if any) is going to be written (in CSV format). *Default value*: `snapshot.csv`.|
|`specializeSQLGeneSelection`| __Boolean__. Whether to specialize sql gene selection to mutation. *Default value*: `true`.|
|`startNumberOfMutations`| __Int__. Number of applied mutations on sampled individuals, at the start of the search. *Constraints*: `min=0.0`. *Default value*: `1`.|
|`statisticsColumnId`| __String__. An id that will be part as a column of the statistics file (if any is generated). *Default value*: `-`.|
|`statisticsFile`| __String__. Where the statistics file (if any) is going to be written (in CSV format). *Default value*: `statistics.csv`.|
|`stoppingCriterion`| __Enum__. Stopping criterion for the search. *Valid values*: `TIME, FITNESS_EVALUATIONS`. *Default value*: `TIME`.|
|`structureMutationProbability`| __Double__. Probability of applying a mutation that can change the structure of a test. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.5`.|
|`sutControllerHost`| __String__. Host name or IP address of where the SUT REST controller is listening on. *Default value*: `localhost`.|
|`sutControllerPort`| __Int__. TCP port of where the SUT REST controller is listening on. *Constraints*: `min=0.0, max=65535.0`. *Default value*: `40100`.|
|`tcpTimeoutMs`| __Int__. Number of milliseconds we are going to wait to get a response on a TCP connection, e.g., when making HTTP calls to a Web API. *Default value*: `30000`.|
|`testSuiteFileName`| __String__. DEPRECATED. Rather use _outputFilePrefix_ and _outputFileSuffix_. *Default value*: `""`.|
|`testSuiteSplitType`| __Enum__. Instead of generating a single test file, it could be split in several files, according to different strategies. *Valid values*: `NONE, CLUSTER, CODE`. *Default value*: `CLUSTER`.|
|`tournamentSize`| __Int__. Number of elements to consider in a Tournament Selection (if any is used in the search algorithm). *Constraints*: `min=1.0`. *Default value*: `10`.|
|`useMethodReplacement`| __Boolean__. Apply method replacement heuristics to smooth the search landscape. Note that the method replacement instrumentations would still be applied, it is just that their testing targets will be ignored in the fitness function if this option is set to false. *Default value*: `true`.|
|`useNonIntegerReplacement`| __Boolean__. Apply non-integer numeric comparison heuristics to smooth the search landscape. *Default value*: `true`.|
|`useTimeInFeedbackSampling`| __Boolean__. Whether to use timestamp info on the execution time of the tests for sampling (e.g., to reward the quickest ones). *Default value*: `true`.|
|`weightBasedMutationRate`| __Boolean__. Whether to enable a weight-based mutation rate. *Default value*: `true`.|
|`writeExtraHeuristicsFile`| __Boolean__. Whether we should collect data on the extra heuristics. Only needed for experiments. *Default value*: `false`.|
|`writeStatistics`| __Boolean__. Whether or not writing statistics of the search process. This is only needed when running experiments with different parameter settings. *Default value*: `false`.|
|`xoverProbability`| __Double__. Probability of applying crossover operation (if any is used in the search algorithm). *Constraints*: `probability 0.0-1.0`. *Default value*: `0.7`.|

## Experimental Command-Line Options

|Options|Description|
|---|---|
|`S1dR`| __Double__. Specify a probability to apply S1dR when resource sampling strategy is 'Customized'. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.25`.|
|`S1iR`| __Double__. Specify a probability to apply S1iR when resource sampling strategy is 'Customized'. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.25`.|
|`S2dR`| __Double__. Specify a probability to apply S2dR when resource sampling strategy is 'Customized'. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.25`.|
|`SMdR`| __Double__. Specify a probability to apply SMdR when resource sampling strategy is 'Customized'. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.25`.|
|`abstractInitializationGeneToMutate`| __Boolean__. During mutation, whether to abstract genes for repeated SQL actions. *Default value*: `false`.|
|`archiveAfterMutationFile`| __String__. Specify a path to save archive after each mutation during search, only useful for debugging. *Default value*: `archive.csv`.|
|`coveredTargetSortedBy`| __Enum__. Specify a format to organize the covered targets by the search. *Valid values*: `NAME, TEST`. *Default value*: `NAME`.|
|`dependencyFile`| __String__. Specify a file that saves derived dependencies. *Default value*: `dependencies.csv`.|
|`doCollectImpact`| __Boolean__. Specify whether to collect impact info that provides an option to enable of collecting impact info when archive-based gene selection is disable. *Default value*: `false`.|
|`dpcTargetTestSize`| __Int__. Specify a max size of a test to be targeted when either DPC_INCREASING or DPC_DECREASING is enabeld. *Default value*: `1`.|
|`employResourceSizeHandlingStrategy`| __Enum__. Specify a strategy to determinate a number of resources to be manipulated throughout the search. *Valid values*: `NONE, RANDOM, DPC`. *Default value*: `NONE`.|
|`employSmartDbClean`| __Boolean__. Specify whether to employ smart database clean to clear data in the database if the SUT has.`null` represents to employ the setting specified on the EM driver side. *Default value*: `null`.|
|`enableAdaptiveResourceStructureMutation`| __Boolean__. Specify whether to decide the resource-based structure mutator and resource to be mutated adaptively based on impacts during focused search.Note that it only works when resource-based solution is enabled for solving REST problem. *Default value*: `false`.|
|`enableNLPParser`| __Boolean__. Whether to employ NLP parser to process text. Note that to enable this parser, it is required to build the EvoMaster with the resource profile, i.e., mvn clean install -Presourceexp -DskipTests. *Default value*: `false`.|
|`enableProcessMonitor`| __Boolean__. Whether or not enable a search process monitor for archiving evaluated individuals and Archive regarding an evaluation of search. This is only needed when running experiments with different parameter settings. *Default value*: `false`.|
|`enablePureRPCTestGeneration`| __Boolean__. Whether to generate RPC endpoint invocation which is independent from EM driver. *Default value*: `false`.|
|`enableRPCAssertionWithInstance`| __Boolean__. Whether to generate RPC Assertions based on response instance. *Default value*: `false`.|
|`enableRPCCustomizedResponseTargets`| __Boolean__. Whether to enable customized responses indicating business logic. *Default value*: `false`.|
|`enableRPCCustomizedTestOutput`| __Boolean__. Whether to enable customized RPC Test output if 'customizeRPCTestOutput' is implemented. *Default value*: `false`.|
|`enableRPCExtraResponseTargets`| __Boolean__. Whether to enable extra targets for responses, e.g., regarding nullable response, having extra targets for whether it is null. *Default value*: `false`.|
|`enableTrackIndividual`| __Boolean__. Whether to enable tracking the history of modifications of the individuals during the search. *Default value*: `false`.|
|`enableWriteSnapshotTests`| __Boolean__. Enable to print snapshots of the generated tests during the search in an interval defined in snapshotsInterval. *Default value*: `false`.|
|`errorTextEpsilon`| __Double__. The Distance Metric Error Text may use several values for epsilon.During experimentation, it may be useful to adjust these values. Epsilon describes the size of the neighbourhood used for clustering, so may result in different clustering results.Epsilon should be between 0.0 and 1.0. If the value is outside of that range, epsilon will use the default of 0.8. *Constraints*: `min=0.0, max=1.0`. *Default value*: `0.8`.|
|`exceedTargetsFile`| __String__. Specify a path to save all not covered targets when the number is more than 100. *Default value*: `exceedTargets.txt`.|
|`exportDependencies`| __Boolean__. Specify whether to export derived dependencies among resources. *Default value*: `false`.|
|`exportImpacts`| __Boolean__. Specify whether to export derived impacts among genes. *Default value*: `false`.|
|`externalServiceIP`| __String__. User provided external service IP. *Constraints*: `regex ^127\.((25[0-5]\|2[0-4][0-9]\|[01]?[0-9][0-9]?)\.){2}(25[0-5]\|2[0-4][0-9]\|[01]?[0-9][0-9]?)$`. *Default value*: `127.0.0.2`.|
|`externalServiceIPSelectionStrategy`| __Enum__. Specify a method to select the first external service spoof IP address. *Valid values*: `NONE, DEFAULT, USER, RANDOM`. *Default value*: `NONE`.|
|`extraHeader`| __Boolean__. Add an extra HTTP header, to analyze how it is used/read by the SUT. Needed to discover new headers that were not specified in the schema. *Default value*: `false`.|
|`extraQueryParam`| __Boolean__. Add an extra query param, to analyze how it is used/read by the SUT. Needed to discover new query params that were not specified in the schema. *Default value*: `false`.|
|`forceSqlAllColumnInsertion`| __Boolean__. Force filling data of all columns when inserting new row, instead of only minimal required set. *Default value*: `false`.|
|`generateSqlDataWithDSE`| __Boolean__. Enable EvoMaster to generate SQL data with direct accesses to the database. Use Dynamic Symbolic Execution. *Default value*: `false`.|
|`impactAfterMutationFile`| __String__. Specify a path to save collected impact info after each mutation during search, only useful for debugging. *Default value*: `impactSnapshot.csv`.|
|`impactFile`| __String__. Specify a path to save derived genes. *Default value*: `impact.csv`.|
|`initStructureMutationProbability`| __Double__. Probability of applying a mutation that can change the structure of test's initialization if it has. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.0`.|
|`instrumentMR_NET`| __Boolean__. Execute instrumentation for method replace with category NET. Note: this applies only for languages in which instrumentation is applied at runtime, like Java/Kotlin on the JVM. *Default value*: `false`.|
|`jaCoCoAgentLocation`| __String__. Path on filesystem of where JaCoCo Agent jar file is located. Option meaningful only for External Drivers for JVM. If left empty, it is not used. Note that this only impact the generated output test cases. *Constraints*: `regex (.*jacoco.*\.jar)\|(^$)`. *Default value*: `""`.|
|`jaCoCoCliLocation`| __String__. Path on filesystem of where JaCoCo CLI jar file is located. Option meaningful only for External Drivers for JVM. If left empty, it is not used. Note that this only impact the generated output test cases. *Constraints*: `regex (.*jacoco.*\.jar)\|(^$)`. *Default value*: `""`.|
|`jaCoCoOutputFile`| __String__. Destination file for JaCoCo. Option meaningful only for External Drivers for JVM. If left empty, it is not used. Note that this only impact the generated output test cases. *Default value*: `""`.|
|`jaCoCoPort`| __Int__. Port used by JaCoCo to export coverage reports. *Constraints*: `min=0.0, max=65535.0`. *Default value*: `8899`.|
|`javaCommand`| __String__. Command for 'java' used in the External Drivers. Useful for when there are different JDK installed on same machine without the need to update JAVA_HOME. Note that this only impact the generated output test cases. *Default value*: `java`.|
|`lastLineEpsilon`| __Double__. The Distance Metric Last Line may use several values for epsilon.During experimentation, it may be useful to adjust these values. Epsilon describes the size of the neighbourhood used for clustering, so may result in different clustering results.Epsilon should be between 0.0 and 1.0. If the value is outside of that range, epsilon will use the default of 0.8. *Constraints*: `min=0.0, max=1.0`. *Default value*: `0.8`.|
|`maxAssertionForDataInCollection`| __Int__. Specify a maximum number of data in a collection to be asserted in the generated tests. Note that zero means that only the size of the collection will be asserted. A negative value means all data in the collection will be asserted (i.e., no limit). *Default value*: `3`.|
|`maxLengthOfTraces`| __Int__. Specify a maxLength of tracking when enableTrackIndividual or enableTrackEvaluatedIndividual is true. Note that the value should be specified with a non-negative number or -1 (for tracking all history). *Constraints*: `min=-1.0`. *Default value*: `10`.|
|`maxResourceSize`| __Int__. Specify a max size of resources in a test. 0 means the there is no specified restriction on a number of resources. *Constraints*: `min=0.0`. *Default value*: `0`.|
|`maxSizeOfHandlingResource`| __Int__. Specify a maximum number of handling (remove/add) resource size at once, e.g., add 3 resource at most. *Constraints*: `min=0.0`. *Default value*: `0`.|
|`maxSizeOfMutatingInitAction`| __Int__. Specify a maximum number of handling (remove/add) init actions at once, e.g., add 3 init actions at most. *Constraints*: `min=0.0`. *Default value*: `0`.|
|`maxTestSizeStrategy`| __Enum__. Specify a strategy to handle a max size of a test. *Valid values*: `SPECIFIED, DPC_INCREASING, DPC_DECREASING`. *Default value*: `SPECIFIED`.|
|`maximumExistingDataToSampleInDb`| __Int__. Specify a maximum number of existing data in the database to sample when SQL handling is enabled. Note that a negative number means all existing data would be sampled. *Default value*: `-1`.|
|`minRowOfTable`| __Int__. Specify a minimal number of rows in a table that enables selection (i.e., SELECT sql) to prepare resources for REST Action. In other word, if the number is less than the specified, insertion is always applied. *Constraints*: `min=0.0`. *Default value*: `10`.|
|`mutatedGeneFile`| __String__. Specify a path to save mutation details which is useful for debugging mutation. *Default value*: `mutatedGeneInfo.csv`.|
|`mutationTargetsSelectionStrategy`| __Enum__. Specify a strategy to select targets for evaluating mutation. *Valid values*: `FIRST_NOT_COVERED_TARGET, EXPANDED_UPDATED_NOT_COVERED_TARGET, UPDATED_NOT_COVERED_TARGET`. *Default value*: `FIRST_NOT_COVERED_TARGET`.|
|`outputExecutedSQL`| __Enum__. Whether to output executed sql info. *Valid values*: `NONE, ALL_AT_END, ONCE_EXECUTED`. *Default value*: `NONE`.|
|`probOfHandlingLength`| __Double__. Specify a probability of applying length handling. *Default value*: `0.0`.|
|`probOfHarvestingResponsesFromActualExternalServices`| __Double__. a probability of harvesting actual responses from external services as seeds. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.0`.|
|`probOfMutatingResponsesBasedOnActualResponse`| __Double__. a probability of mutating mocked responses based on actual responses. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.0`.|
|`probOfSelectFromDatabase`| __Double__. Specify a probability that enables selection (i.e., SELECT sql) of data from database instead of insertion (i.e., INSERT sql) for preparing resources for REST actions. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.1`.|
|`probOfSmartInitStructureMutator`| __Double__. Specify a probability of applying a smart structure mutator for initialization of the individual. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.0`.|
|`processFiles`| __String__. Specify a folder to save results when a search monitor is enabled. *Default value*: `process_data`.|
|`processFormat`| __Enum__. Specify a format to save the process data. *Valid values*: `JSON_ALL, TEST_IND, TARGET_TEST_IND`. *Default value*: `JSON_ALL`.|
|`processInterval`| __Double__. Specify how often to save results when a search monitor is enabled, and 0.0 presents to record all evaluated individual. *Constraints*: `min=0.0, max=50.0`. *Default value*: `0.0`.|
|`recordExceededTargets`| __Boolean__. Whether to record targets when the number is more than 100. *Default value*: `false`.|
|`saveArchiveAfterMutation`| __Boolean__. Whether to save archive info after each of mutation, which is typically useful for debugging mutation and archive. *Default value*: `false`.|
|`saveExecutedSQLToFile`| __String__. Specify a path to save all executed sql commands to a file (default is 'sql.txt'). *Default value*: `sql.txt`.|
|`saveImpactAfterMutation`| __Boolean__. Whether to save impact info after each of mutation, which is typically useful debugging impact driven solutions and mutation. *Default value*: `false`.|
|`saveMutationInfo`| __Boolean__. Whether to save mutated gene info, which is typically used for debugging mutation. *Default value*: `false`.|
|`searchPercentageExtraHandling`| __Double__. Percentage [0.0,1.0] of elapsed time in the search while trying to infer any extra query parameter and header. After this time has passed, those attempts stop. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.1`.|
|`seedTestCases`| __Boolean__. Whether to seed EvoMaster with some initial test cases. These test cases will be used and evolved throughout the search process. *Default value*: `false`.|
|`seedTestCasesFormat`| __Enum__. Format of the test cases seeded to EvoMaster. *Valid values*: `POSTMAN`. *Default value*: `POSTMAN`.|
|`seedTestCasesPath`| __String__. File path where the seeded test cases are located. *Default value*: `postman.postman_collection.json`.|
|`startingPerOfGenesToMutate`| __Double__. Specify a starting percentage of genes of an individual to mutate. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.5`.|
|`structureMutationProFS`| __Double__. Specify a probability of applying structure mutator during the focused search. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.0`.|
|`structureMutationProbStrategy`| __Enum__. Specify a strategy to handle a probability of applying structure mutator during the focused search. *Valid values*: `SPECIFIED, SPECIFIED_FS, DPC_TO_SPECIFIED_BEFORE_FS, DPC_TO_SPECIFIED_AFTER_FS, ADAPTIVE_WITH_IMPACT`. *Default value*: `SPECIFIED`.|
|`taintOnSampling`| __Boolean__. Whether input tracking is used on sampling time, besides mutation time. *Default value*: `false`.|
|`treeDepth`| __Int__. Maximum tree depth in mutations/queries to be evaluated. This is to avoid issues when dealing with huge graphs in GraphQL. *Constraints*: `min=1.0`. *Default value*: `4`.|
|`useExtraSqlDbConstraintsProbability`| __Double__. Whether to analyze how SQL databases are accessed to infer extra constraints from the business logic. An example is javax/jakarta annotation constraints defined on JPA entities. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.0`.|
|`useGlobalTaintInfoProbability`| __Double__. When sampling new individual, check whether to use already existing info on tainted values. *Constraints*: `probability 0.0-1.0`. *Default value*: `0.0`.|
|`useWeightedSampling`| __Boolean__. When sampling from archive based on targets, decide whether to use weights based on properties of the targets (e.g., a target likely leading to a flag will be sampled less often). *Default value*: `false`.|
|`writeSnapshotTestsIntervalInSeconds`| __Int__. The size (in seconds) of the interval that the snapshots will be printed, if enabled. *Default value*: `3600`.|
