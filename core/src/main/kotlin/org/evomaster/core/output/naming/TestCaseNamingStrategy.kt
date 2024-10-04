package org.evomaster.core.output.naming

import org.evomaster.core.output.TestCase
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.Solution

/**
 * A naming strategy will help provide names to the generated test cases.
 * Naming can rely upon information contained in the solution provided.
 */
abstract class TestCaseNamingStrategy(
    protected val solution: Solution<*>
) {

    /**
     * @return the list of TestCase with the generated name given the naming strategy
     */
    abstract fun getTestCases(): List<TestCase>

    /**
     * @param comparators used to sort the test cases
     *
     * @return the list of sorted TestCase with the generated name given the naming strategy
     */
    abstract fun getSortedTestCases(comparators: List<Comparator<EvaluatedIndividual<*>>>): List<TestCase>

    /**
     * @param individual containing information for the test about to be named
     *
     * @return a String with extra information that will be included in the test name, regarding the EvaluatedIndividual
     */
    protected abstract fun expandName(individual: EvaluatedIndividual<*>, nameTokens: MutableList<String>): String

}
