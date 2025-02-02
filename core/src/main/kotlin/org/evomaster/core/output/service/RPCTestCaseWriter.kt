package org.evomaster.core.output.service

import com.google.inject.Inject
import org.evomaster.core.output.Lines
import org.evomaster.core.output.TestCase
import org.evomaster.core.output.formatter.OutputFormatter
import org.evomaster.core.problem.rpc.RPCCallAction
import org.evomaster.core.problem.rpc.RPCCallResult
import org.evomaster.core.problem.rpc.RPCIndividual
import org.evomaster.core.problem.rpc.service.RPCEndpointsHandler
import org.evomaster.core.search.Action
import org.evomaster.core.search.ActionResult
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.gene.utils.GeneUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.max

/**
 * created by manzhang on 2021/11/26
 */
class RPCTestCaseWriter : ApiTestCaseWriter() {

    companion object{
        private val log: Logger = LoggerFactory.getLogger(RPCTestCaseWriter::class.java)
    }

    @Inject
    protected lateinit var rpcHandler: RPCEndpointsHandler

    override fun handleActionCalls(
        lines: Lines,
        baseUrlOfSut: String,
        ind: EvaluatedIndividual<*>,
        insertionVars: MutableList<Pair<String, String>>
    ) {

        if (ind.individual is RPCIndividual){
            ind.evaluatedMainActions().forEach { evaluatedAction->

                lines.addEmpty()

                val call = evaluatedAction.action as RPCCallAction
                val res = evaluatedAction.result as RPCCallResult

                if (res.failedCall()) {
                    addActionInTryCatch(call, lines, res, baseUrlOfSut)
                } else {
                    addActionLines(call, lines, res, baseUrlOfSut)
                }
            }
        }
    }

    override fun addActionLines(action: Action, lines: Lines, result: ActionResult, baseUrlOfSut: String) {

        val rpcCallAction = (action as? RPCCallAction)?: throw IllegalStateException("action must be RPCCallAction, but it is ${action::class.java.simpleName}")
        val rpcCallResult = (result as? RPCCallResult)?: throw IllegalStateException("result must be RPCCallResult, but it is ${action::class.java.simpleName}")



        val resVarName = createUniqueResponseVariableName()

        lines.addEmpty()

        // null varName representing that the test script generation fails, then skip its assertions
        val varName = handleActionExecution(lines, resVarName, rpcCallResult, rpcCallAction)
        // append additional info after the execution, eg, last statement
        appendAdditionalInfo(lines, rpcCallResult)

        if (config.enableBasicAssertions){
            if (rpcCallAction.response!=null && varName != null){
                if (rpcCallResult.hasResponse())
                    handleAssertions(lines, varName, rpcCallResult)
                else if(!rpcCallResult.isExceptionThrown()){
                    handleAssertNull(lines, varName)
                }
            }
        }
    }

    private fun handleAssertNull(lines: Lines, resVarName: String){
        if (format.isJava()){
            lines.add("assertNull($resVarName)")
            lines.appendSemicolon(config.outputFormat)
        }

    }

    private fun handleAssertions(lines: Lines, resVarName: String, rpcCallResult: RPCCallResult){
//        val responseBody = rpcCallResult.getResponseJsonValue()

        if (config.enableRPCAssertionWithInstance){
            if (rpcCallResult.getAssertionScript() != null)
                rpcCallResult.getAssertionScript()!!.split(System.lineSeparator()).forEach {
                    lines.add(it)
                }
        }

    }

    private fun handleActionExecution(lines: Lines, resVarName: String, rpcCallResult: RPCCallResult, rpcCallAction: RPCCallAction): String?{

        if (config.enablePureRPCTestGeneration){
            val script = rpcCallResult.getTestScript()
            if (script != null){
                script.split(System.lineSeparator()).forEach {
                    lines.add(it)
                }
                return rpcCallResult.getResponseVariableName()?: throw IllegalStateException("missing variable name of response")
            }else{
                log.warn("fail to get test script from em driver")
                executeActionWithSutHandler(lines, resVarName, rpcCallAction)
                return null
            }
        }else{
            val authAction = rpcHandler.getRPCAuthActionDto(rpcCallAction)
            if (authAction!=null){
                // check if it is local
                if (authAction.clientInfo == null){
                    val authInfo = "\"" + GeneUtils.applyEscapes(authAction.requestParams[0].stringValue, GeneUtils.EscapeMode.JSON, format) +"\""

                    if (format.isJavaOrKotlin()){
                        lines.add(TestSuiteWriter.controller+"."+authAction.actionName+"("+authInfo+")")

                        lines.appendSemicolon(format)
                    }

                }else
                    executeActionWithSutHandler(lines, resVarName+"_auth_"+rpcCallAction.auth.authIndex, rpcHandler.getRPCActionDtoJson(authAction))
            }
            executeActionWithSutHandler(lines, resVarName, rpcCallAction)
        }
        return resVarName
    }

    private fun appendAdditionalInfo(lines: Lines, result: RPCCallResult){
        // here, we report internal error and unexpected exception as potential faults
        if (config.outputFormat.isJavaOrKotlin() && result.hasPotentialFault()){
            lines.append("// ${result.getLastStatementForPotentialBug()}")
            if (result.isExceptionThrown())
                lines.append(" ${result.getExceptionInfo()}")
        }

    }

    override fun shouldFailIfException(result: ActionResult): Boolean {
        //TODO Man: need a further check
        return false
    }

    private fun executeActionWithSutHandler(lines: Lines, resVarName: String, rpcCallAction: RPCCallAction){
        val executionJson = rpcHandler.getRPCActionJson(rpcCallAction)
        executeActionWithSutHandler(lines, resVarName, executionJson)

    }
    private fun executeActionWithSutHandler(lines: Lines, resVarName: String, executionJson: String){

        when {
            format.isKotlin() -> lines.add("val $resVarName = ${TestSuiteWriter.controller}.executeRPCEndpoint(")
            format.isJava() -> lines.add("Object $resVarName = ${TestSuiteWriter.controller}.executeRPCEndpoint(")
        }

        printExecutionJson(executionJson, lines)

        when {
            format.isKotlin() -> lines.append(")")
            format.isJava() -> lines.append(");")
        }
    }

    private fun printExecutionJson(json: String, lines: Lines) {

        val body = if (OutputFormatter.JSON_FORMATTER.isValid(json)) {
            OutputFormatter.JSON_FORMATTER.getFormatted(json)
        } else {
            json
        }

        val bodyLines = body.split("\n").map { s ->
            // after applyEscapes, somehow, the format is changed, then count space here
            countFirstSpace(s) to "\" " + GeneUtils.applyEscapes(s.trim(), mode = GeneUtils.EscapeMode.BODY, format = format) + " \""
        }

        printBodyLines(lines, bodyLines)
    }

    private fun printBodyLines(lines: Lines, bodyLines: List<Pair<Int, String>>){
        lines.indented {
            bodyLines.forEachIndexed { index, line->
                lines.add(nSpace(line.first)+ line.second + (if (index != bodyLines.size - 1) "+" else ""))
            }
        }
    }

    private fun countFirstSpace(line: String) : Int{
        return max(0, line.indexOfFirst { it != ' ' })
    }

    private fun nSpace(n: Int): String{
        return (0 until n).joinToString("") { " " }
    }


    override fun addExtraInitStatement(lines: Lines) {
        if (!config.enablePureRPCTestGeneration) return

        val clientVariables = rpcHandler.getClientAndItsVariable()
        clientVariables.forEach { (t, u)->
            val getClient = "${TestSuiteWriter.controller}.getRPCClient(\"${u.second}\")"
            when{
                config.outputFormat.isKotlin()-> lines.add("$t = $getClient as ${handleClientType(u.first)}")
                config.outputFormat.isJava() -> lines.add("$t = (${handleClientType(u.first)}) $getClient")
                else -> throw IllegalStateException("NOT SUPPORT for the format : ${config.outputFormat}")
            }
            lines.appendSemicolon(format)
        }
    }

    override fun addExtraStaticVariables(lines: Lines) {
        if (!config.enablePureRPCTestGeneration) return

        val clientVariables = rpcHandler.getClientAndItsVariable()
        clientVariables.forEach { (t, u)->
            when{
                config.outputFormat.isKotlin()-> lines.add("private lateinit var $t: ${handleClientType(u.first)}")
                config.outputFormat.isJava() -> lines.add("private static ${handleClientType(u.first)} $t")
                else -> throw IllegalStateException("NOT SUPPORT for the format : ${config.outputFormat}")
            }
            lines.appendSemicolon(format)
        }
    }

    /*
        the inner class in java could be represented with $ in string format
        for instance org.thrift.ncs.client.NcsService$Client,
        then we need to further handle it
     */
    private fun handleClientType(clientType: String) = clientType.replace("\$",".")

    override fun additionalTestHandling(tests: List<TestCase>) {
        if (!config.enableRPCCustomizedTestOutput) return

        try {
            rpcHandler.handleCustomizedTests(tests.map { t-> t.test as EvaluatedIndividual<RPCIndividual> })
        }catch (e : Exception){
            log.warn("Fail to handle customized tests: ${e.message}")
        }
    }

}