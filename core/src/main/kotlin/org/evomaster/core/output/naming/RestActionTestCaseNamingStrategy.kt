package org.evomaster.core.output.naming

import org.evomaster.core.problem.httpws.HttpWsCallResult
import org.evomaster.core.problem.rest.RestCallAction
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.Solution
import org.evomaster.core.search.action.EvaluatedAction

open class RestActionTestCaseNamingStrategy(
    solution: Solution<*>,
    languageConventionFormatter: LanguageConventionFormatter
) : ActionTestCaseNamingStrategy(solution, languageConventionFormatter)  {

    override fun expandName(individual: EvaluatedIndividual<*>, nameTokens: MutableList<String>): String {
        var evaluatedAction = individual.evaluatedMainActions().last()
        var action = evaluatedAction.action as RestCallAction

        nameTokens.add(action.verb.toString())
        nameTokens.add(on)
        nameTokens.add(getPath(action.path.nameQualifier))
        addResult(individual, nameTokens)

        return formatName(nameTokens)
    }

    override fun addActionResult(evaluatedAction: EvaluatedAction, nameTokens: MutableList<String>) {
        var result = evaluatedAction.result as HttpWsCallResult
        nameTokens.add(returns)
        nameTokens.add(result.getStatusCode().toString())
    }

}
