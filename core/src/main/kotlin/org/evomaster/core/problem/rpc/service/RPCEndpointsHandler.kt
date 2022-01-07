package org.evomaster.core.problem.rpc.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.Inject
import org.evomaster.client.java.controller.api.dto.AuthenticationDto
import org.evomaster.client.java.controller.api.dto.SutInfoDto
import org.evomaster.client.java.controller.api.dto.problem.RPCProblemDto
import org.evomaster.client.java.controller.api.dto.problem.rpc.ParamDto
import org.evomaster.client.java.controller.api.dto.problem.rpc.RPCActionDto
import org.evomaster.client.java.controller.api.dto.problem.rpc.RPCSupportedDataType
import org.evomaster.core.EMConfig
import org.evomaster.core.Lazy
import org.evomaster.core.logging.LoggingUtil
import org.evomaster.core.output.service.TestSuiteWriter
import org.evomaster.core.problem.api.service.param.Param
import org.evomaster.core.problem.rpc.RPCCallAction
import org.evomaster.core.problem.rpc.auth.RPCAuthenticationInfo
import org.evomaster.core.problem.rpc.auth.RPCNoAuth
import org.evomaster.core.problem.rpc.param.RPCParam
import org.evomaster.core.problem.util.ParamUtil
import org.evomaster.core.search.Action
import org.evomaster.core.search.gene.*
import org.evomaster.core.search.gene.datetime.DateTimeGene
import org.evomaster.core.search.impact.impactinfocollection.value.SeededGeneImpact
import org.evomaster.core.search.service.Randomness
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * this class is used to manage formulated individual with schemas of SUT
 */
class RPCEndpointsHandler {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RPCEndpointsHandler::class.java)
    }

    @Inject
    protected lateinit var config: EMConfig

    @Inject
    private lateinit var randomness: Randomness


    /**
     * a list of available auth info configured through driver by user, retrieving from em driver side
     */
    protected val authentications: MutableMap<Int, RPCAuthenticationInfo> = mutableMapOf()

    /**
     * key is an id of the endpoint, ie, interface name: action name
     * value is corresponding endpoint schema
     */
    private val actionSchemaCluster = mutableMapOf<String, RPCActionDto>()

    /**
     * a map of authorizedAction with available auth info
     * - Key is the id of action (which is consistent with key of [actionSchemaCluster])
     * - Value is a list of auth (which is based on key of [authentications])
     */
//    private val authorizedActionAuthMap = mutableMapOf<String, MutableList<Int>>()


    /**
     * a map of actions with available customized candidates
     * - Key is the id of action (which is consistent with key of [actionSchemaCluster])
     * - Value is a set of customized candidates (which is based on index of customization info)
     */
    private val actionWithCustomizedCandidatesMap = mutableMapOf<String, MutableSet<String>>()

    /**
     * key is type in the schema
     * value is object gene for it
     */
    private val typeCache = mutableMapOf<String, Gene>()

    private val objectMapper = ObjectMapper()

    /**
     * @param actionId is an id of the endpoint
     * @return action dto which contains info for its execution, eg, client, method name in the interface
     */
    fun getActionDto(actionId : String) : RPCActionDto{
        return actionSchemaCluster[actionId]?: throw IllegalStateException("could not find the $actionId")
    }

    private fun setAuthInfo(infoDto: SutInfoDto){
        infoDto.infoForAuthentication?:return

        infoDto.infoForAuthentication.forEachIndexed { index, dto ->
            if (!handleRPCAuthDto(index, dto))
                log.warn("auth info at $index is not handled by RPC auth")
        }
    }

    private fun handleRPCAuthDto(index: Int, auth: AuthenticationDto) : Boolean{
        if (auth.jsonAuthEndpoint == null)
            return false
        if (auth.jsonAuthEndpoint != null){
            authentications[index] = RPCAuthenticationInfo(auth.name?:"untitled",
                index,
                auth.jsonAuthEndpoint.annotationOnEndpoint == null,
                null, auth.jsonAuthEndpoint.endpointName,
            )
        }

        return true
    }

    /**
     * setup auth for [action] with an auth info at random
     */
    fun actionWithRandomAuth(action: RPCCallAction){

        val gs = authentications.values.filter { it.isGlobal }
        if (gs.isNotEmpty())
            action.auth = randomness.choose(gs)
        else
            action.auth = RPCNoAuth()
//        if (action is AuthorizedRPCCallAction){
//            val ss = authorizedActionAuthMap[action.id]
//            if (!ss.isNullOrEmpty()){
//                val sId = randomness.choose(ss)
//                action.requiredAuth = authentications[sId]?:throw IllegalStateException("could not find auth with id $sId in authentication map")
//            }else
//                action.requiredAuth = RPCNoAuth()
//        }
    }

    /**
     * setup auth info for [action] with all auth candidates, ie, [authorizedActionAuthMap] and [authentications]
     */
    fun actionWithAllAuth(action: RPCCallAction): List<RPCCallAction>{
        val results = mutableListOf<RPCCallAction>()
        authentications.values.filter {it.isGlobal}.plus(listOf(RPCNoAuth())).forEach { u ->
            val actionWithAuth = action.copy()

//            if (action is AuthorizedRPCCallAction){
//                (actionWithAuth as AuthorizedRPCCallAction).auth = u
//                actionWithAuth.requiredAuth = RPCNoAuth()
//                results.add(actionWithAuth)
//
//                authorizedActionAuthMap[action.id]?.forEach { ru->
//                    val actionWithRAuth = actionWithAuth.copy() as AuthorizedRPCCallAction
//                    actionWithAuth.requiredAuth = authentications[ru]?:throw IllegalStateException("could not find auth with id $ru in authentication map")
//                    results.add(actionWithRAuth)
//                }
//            }else{
                (actionWithAuth as RPCCallAction).auth = u
                results.add(actionWithAuth)
//            }

        }

        if (results.isEmpty())
            throw IllegalStateException("should return at least the action itself")

        return results
    }

    fun actionWithAllCandidates(action: RPCCallAction): List<RPCCallAction>{
        val results = mutableListOf<RPCCallAction>()
        actionWithCustomizedCandidatesMap[action.id]?.forEach {
            results.add((action.copy() as RPCCallAction).apply { handleActionWithSeededCandidates(this, it) })
        }
        val noSeed = action.copy() as RPCCallAction
        handleActionNoSeededCandidates(noSeed)
        results.add(noSeed)
        return results;
    }

    fun actionWithRandomSeeded(action: RPCCallAction, noSeedProbability: Double): RPCCallAction{
        val candidates = actionWithCustomizedCandidatesMap[action.id]
        if (candidates== null || candidates.isEmpty()) return action
        if (randomness.nextBoolean(noSeedProbability))
            handleActionNoSeededCandidates(action)
        else{
            val selected = randomness.choose(candidates)
            handleActionWithSeededCandidates(action, selected)
        }
        return action
    }


    private fun handleActionWithSeededCandidates(action: RPCCallAction, candidateKey: String){
        action.seeGenes().flatMap { it.flatView() }.filter { it is DisruptiveGene<*> && it.gene is SeededGene<*> }.forEach { g->
            val index = ((g as DisruptiveGene<*>).gene as SeededGene<*>).seeded.values.indexOfFirst { it.name == candidateKey }
            if (index != -1){
                (g.gene as SeededGene<*>).employSeeded = true
                g.gene.seeded.index = index
            }
        }
    }

    private fun handleActionNoSeededCandidates(action: RPCCallAction){
        action.seeGenes().filter { it is DisruptiveGene<*> && it.gene is SeededGene<*> }.forEach { g->
            ((g as DisruptiveGene<*>).gene as SeededGene<*>).employSeeded = false
            (g.gene as SeededGene<*>).gene.randomize(randomness, false)
        }
    }

    /**
     * reset [actionCluster] based on interface schemas specified in [problem]
     */
    fun initActionCluster(problem: RPCProblemDto, actionCluster: MutableMap<String, Action>, infoDto: SutInfoDto){

        setAuthInfo(infoDto)

        problem.schemas.forEach { i->
            i.types.sortedBy { it.type.depth }
                .filter { it.type.type == RPCSupportedDataType.CUSTOM_OBJECT }.forEach { t ->
                typeCache[t.type.fullTypeName] = handleObjectType(t)
            }

        }

        actionCluster.clear()
        problem.schemas.forEach { i->
            i.endpoints.forEach{e->
                actionSchemaCluster.putIfAbsent(actionName(i.interfaceId, e.actionName), e)
                val name = actionName(i.interfaceId, e.actionName)
                if (actionCluster.containsKey(name))
                    throw IllegalStateException("$name exists in the actionCluster")
                actionCluster[name] = processEndpoint(name, e, e.isAuthorized)
//                if (e.isAuthorized && e.requiredAuthCandidates != null){
//                    authorizedActionAuthMap[name] = e.requiredAuthCandidates
//                }
                if (e.relatedCustomization != null){
                    actionWithCustomizedCandidatesMap[name] = e.relatedCustomization
                }
            }
        }

        // report statistic of endpoints
        reportEndpointsStatistics(problem.schemas.size, problem.schemas.sumOf { it.skippedEndpoints?.size ?: 0 })
    }

    private fun reportEndpointsStatistics(numSchema: Int, skipped: Int){
        LoggingUtil.getInfoLogger().apply {
            info("There are $numSchema defined RPC interfaces with ${actionSchemaCluster.size} accessible endpoints and $skipped skipped endpoints.")
        }
    }

    /**
     * get rpc action dto based on specified [action]
     */
    fun transformActionDto(action: RPCCallAction, index : Int = -1) : RPCActionDto {
        // generate RPCActionDto
        val rpcAction = actionSchemaCluster[action.id]?.copy()?: throw IllegalStateException("cannot find the ${action.id} in actionSchemaCluster")

        action.parameters.forEach { p->
            if (p is RPCParam){
                p.seeGenes().forEach { g->
                    val paramDto = rpcAction.requestParams.find{ r-> r.name == g.name}?:throw IllegalStateException("cannot find param with a name, ${g.name}")
                    transformGeneToParamDto(g, paramDto)
                }
            }
        }

        rpcAction.doGenerateTestScript = config.enablePureRPCTestGeneration && (index != -1)
        rpcAction.doGenerateAssertions = config.enableRPCAssertionWithInstance

        if (rpcAction.doGenerateTestScript){
            rpcAction.controllerVariable = TestSuiteWriter.controller
        }
        if (rpcAction.doGenerateTestScript || rpcAction.doGenerateAssertions)
            rpcAction.responseVariable = generateResponseVariable(index)

        return rpcAction
    }

    /**
     * generate response variable name for RPC action based on its [index] in a test
     */
    private fun generateResponseVariable(index: Int) = "res_$index"

    /**
     * get rpc action dto with string json based on specified [action]
     * this is only used in test generation
     */
    fun getRPCActionJson(action: RPCCallAction) : String {
        val dto = transformActionDto(action)
        // ignore response param
        dto.responseParam = null
        return objectMapper.writeValueAsString(dto)
    }

    fun getParamDtoJson(dto: ParamDto) : String {
        return objectMapper.writeValueAsString(dto)
    }

    private fun transformGeneToParamDto(gene: Gene, dto: ParamDto){

        if (gene is OptionalGene && !gene.isActive){
            // set null value
            if (gene.gene is ObjectGene || gene.gene is DateTimeGene){
                dto.innerContent = null
            }
            return
        }

        when(val valueGene = ParamUtil.getValueGene(gene)){
            is IntegerGene -> dto.stringValue = valueGene.value.toString()
            is DoubleGene -> dto.stringValue = valueGene.value.toString()
            is FloatGene -> dto.stringValue = valueGene.value.toString()
            is BooleanGene -> dto.stringValue = valueGene.value.toString()
            is StringGene -> dto.stringValue = valueGene.getValueAsRawString()
            is EnumGene<*> -> dto.stringValue = valueGene.index.toString()
            is SeededGene<*> -> dto.stringValue = getValueForSeededGene(valueGene)
            is LongGene -> dto.stringValue = valueGene.value.toString()
            is ArrayGene<*> -> {
                val template = dto.type.example?.copy()?:throw IllegalStateException("a template for a collection is null")
                val innerContent = valueGene.getAllElements().map {
                    val copy = template.copy()
                    transformGeneToParamDto(it, copy)
                    copy
                }
                dto.innerContent = innerContent
            }
            is DateTimeGene -> {
                transformGeneToParamDto(valueGene.date.year, dto.innerContent[0])
                transformGeneToParamDto(valueGene.date.month, dto.innerContent[1])
                transformGeneToParamDto(valueGene.date.day, dto.innerContent[2])
                transformGeneToParamDto(valueGene.time.hour, dto.innerContent[3])
                transformGeneToParamDto(valueGene.time.minute, dto.innerContent[4])
                transformGeneToParamDto(valueGene.time.second, dto.innerContent[5])
            }
            is PairGene<*, *> ->{
                val template = dto.type.example?.copy()
                    ?:throw IllegalStateException("a template for a pair (with dto name: ${dto.name} and gene name: ${gene.name}) is null")
                Lazy.assert { template.innerContent.size == 2 }
                val first = template.innerContent[0]
                transformGeneToParamDto(valueGene.first, first)
                val second = template.innerContent[1]
                transformGeneToParamDto(valueGene.first, second)
                dto.innerContent = listOf(first, second)
            }
            is MapGene<*, *> ->{
                val template = dto.type.example?.copy()
                    ?:throw IllegalStateException("a template for a map dto (with dto name: ${dto.name} and gene name: ${gene.name}) is null")
                val innerContent = valueGene.getAllElements().map {
                    val copy = template.copy()
                    transformGeneToParamDto(it, copy)
                    copy
                }
                dto.innerContent = innerContent
            }
            is ObjectGene -> {
                valueGene.fields.forEach { f->
                    val pdto = dto.innerContent.find { it.name == f.name }
                        ?:throw IllegalStateException("could not find the field (${f.name}) in ParamDto")
                    transformGeneToParamDto(f, pdto)
                }
            }
            else -> throw IllegalStateException("Not support transformGeneToParamDto with gene ${gene::class.java.simpleName} and dto (${dto.type.type})")
        }
    }

    /**
     * set values of [gene] based on dto i.e., [ParamDto]
     * note that it is typically used for handling responses of RPC endpoints
     */
    fun setGeneBasedOnParamDto(gene: Gene, dto: ParamDto){
        if (!isValidToSetValue(gene, dto))
            throw IllegalStateException("the types of gene and its dto are mismatched, i.e., gene (${gene::class.java.simpleName}) vs. dto (${dto.type.type})")
        val valueGene = ParamUtil.getValueGene(gene)

        if (!isNullDto(dto)){
            when(valueGene){
                is IntegerGene -> valueGene.value = dto.stringValue.toInt()
                is DoubleGene -> valueGene.value = dto.stringValue.toDouble()
                is FloatGene -> valueGene.value = dto.stringValue.toFloat()
                is BooleanGene -> valueGene.value = dto.stringValue.toBoolean()
                is StringGene -> valueGene.value = dto.stringValue
                is LongGene -> valueGene.value = dto.stringValue.toLong()
                is EnumGene<*> -> valueGene.index = dto.stringValue.toInt()
                is SeededGene<*> -> TODO()
                is PairGene<*, *> -> {
                    Lazy.assert { dto.innerContent.size == 2 }
                    setGeneBasedOnParamDto(valueGene.first, dto.innerContent[0])
                    setGeneBasedOnParamDto(valueGene.second, dto.innerContent[1])
                }
                is DateTimeGene ->{
                    Lazy.assert { dto.innerContent.size == 6 }
                    setGeneBasedOnParamDto(valueGene.date.year, dto.innerContent[0])
                    setGeneBasedOnParamDto(valueGene.date.month, dto.innerContent[1])
                    setGeneBasedOnParamDto(valueGene.date.day, dto.innerContent[2])
                    setGeneBasedOnParamDto(valueGene.time.hour, dto.innerContent[3])
                    setGeneBasedOnParamDto(valueGene.time.minute, dto.innerContent[4])
                    setGeneBasedOnParamDto(valueGene.time.second, dto.innerContent[5])
                }
                is ArrayGene<*> -> {
                    val template = valueGene.template
                    dto.innerContent.forEach { p->
                        val copy = template.copyContent()
                        setGeneBasedOnParamDto(copy, p)
                        valueGene.addElement(copy)
                    }
                }
                is MapGene<*, *> ->{
                    val template = valueGene.template
                    dto.innerContent.forEach { p->
                        val copy = template.copyContent()
                        setGeneBasedOnParamDto(copy, p)
                        valueGene.addElement(copy)
                    }
                }
                is ObjectGene -> {
                    valueGene.fields.forEach { f->
                        val pdto = dto.innerContent.find { it.name == f.name }
                            ?:throw IllegalStateException("could not find the field (${f.name}) in ParamDto")
                        setGeneBasedOnParamDto(f, pdto)
                    }
                }
                else -> throw IllegalStateException("Not support setGeneBasedOnParamDto with gene ${gene::class.java.simpleName} and dto (${dto.type.type})")
            }
        }else{
            if (gene is OptionalGene && dto.isNullable)
                gene.isActive = false
            else
                log.warn("could not retrieve value of ${dto.name?:"untitled"}")
        }
    }

    private fun isNullDto(dto: ParamDto) : Boolean{
        return when(dto.type.type){
            RPCSupportedDataType.P_INT, RPCSupportedDataType.INT,
            RPCSupportedDataType.P_SHORT, RPCSupportedDataType.SHORT,
            RPCSupportedDataType.P_BYTE, RPCSupportedDataType.BYTE,
            RPCSupportedDataType.P_BOOLEAN, RPCSupportedDataType.BOOLEAN,
            RPCSupportedDataType.P_CHAR, RPCSupportedDataType.CHAR, RPCSupportedDataType.STRING, RPCSupportedDataType.BYTEBUFFER,
            RPCSupportedDataType.P_DOUBLE, RPCSupportedDataType.DOUBLE,
            RPCSupportedDataType.P_FLOAT, RPCSupportedDataType.FLOAT,
            RPCSupportedDataType.P_LONG, RPCSupportedDataType.LONG,
            RPCSupportedDataType.ENUM,
            RPCSupportedDataType.UTIL_DATE, RPCSupportedDataType.CUSTOM_OBJECT -> dto.stringValue == null
            RPCSupportedDataType.ARRAY, RPCSupportedDataType.SET, RPCSupportedDataType.LIST,
            RPCSupportedDataType.MAP,
            RPCSupportedDataType.CUSTOM_CYCLE_OBJECT,
            RPCSupportedDataType.PAIR -> dto.innerContent == null
        }
    }

    /**
     * @return if types of [gene] and its [dto] ie, ParamDto are matched.
     */
    private fun isValidToSetValue(gene: Gene, dto: ParamDto) : Boolean{
        val valueGene = ParamUtil.getValueGene(gene)
        return when(dto.type.type){
            RPCSupportedDataType.P_INT, RPCSupportedDataType.INT,
            RPCSupportedDataType.P_SHORT, RPCSupportedDataType.SHORT,
            RPCSupportedDataType.P_BYTE, RPCSupportedDataType.BYTE -> valueGene is IntegerGene
            RPCSupportedDataType.P_BOOLEAN, RPCSupportedDataType.BOOLEAN -> valueGene is BooleanGene
            RPCSupportedDataType.P_CHAR, RPCSupportedDataType.CHAR, RPCSupportedDataType.STRING, RPCSupportedDataType.BYTEBUFFER -> valueGene is StringGene
            RPCSupportedDataType.P_DOUBLE, RPCSupportedDataType.DOUBLE -> valueGene is DoubleGene
            RPCSupportedDataType.P_FLOAT, RPCSupportedDataType.FLOAT -> valueGene is FloatGene
            RPCSupportedDataType.P_LONG, RPCSupportedDataType.LONG -> valueGene is LongGene
            RPCSupportedDataType.ENUM -> valueGene is MapGene<*,*> || valueGene is EnumGene<*>
            RPCSupportedDataType.ARRAY, RPCSupportedDataType.SET, RPCSupportedDataType.LIST-> valueGene is ArrayGene<*>
            RPCSupportedDataType.MAP -> valueGene is MapGene<*, *>
            RPCSupportedDataType.CUSTOM_OBJECT -> valueGene is ObjectGene || valueGene is MapGene<*,*>
            RPCSupportedDataType.CUSTOM_CYCLE_OBJECT -> valueGene is CycleObjectGene
            RPCSupportedDataType.UTIL_DATE -> valueGene is DateTimeGene
            RPCSupportedDataType.PAIR -> valueGene is PairGene<*,*>
        }
    }

    private fun processEndpoint(name: String, endpointSchema: RPCActionDto, isAuthorized: Boolean) : RPCCallAction{
        val params = mutableListOf<Param>()

        endpointSchema.requestParams.forEach { p->
            val gene = handleDtoParam(p)
            params.add(RPCParam(p.name, gene))
        }

        var response: RPCParam? = null
        // response would be used for assertion generation
        if (endpointSchema.responseParam != null){
            val gene = handleDtoParam(endpointSchema.responseParam)
            response = RPCParam(endpointSchema.responseParam.name, gene)
        }
        /*
            TODO Man exception
         */
//        if (isAuthorized)
//            return AuthorizedRPCCallAction(name, params, responseTemplate = response, response = null)
        return RPCCallAction(name, params, responseTemplate = response, response = null )
    }

    private fun actionName(interfaceName: String, endpointName: String) = "$interfaceName:$endpointName"

    private fun handleDtoParam(param: ParamDto): Gene{
        val gene = when(param.type.type){
            RPCSupportedDataType.P_INT, RPCSupportedDataType.INT -> IntegerGene(param.name, min = param.minValue?.toInt()?: Int.MIN_VALUE, max = param.maxValue?.toInt()?:Int.MAX_VALUE)
            RPCSupportedDataType.P_BOOLEAN, RPCSupportedDataType.BOOLEAN -> BooleanGene(param.name)
            RPCSupportedDataType.P_CHAR, RPCSupportedDataType.CHAR -> StringGene(param.name, value="", maxLength = 1, minLength = param.minSize?.toInt()?:0)
            RPCSupportedDataType.P_DOUBLE, RPCSupportedDataType.DOUBLE -> DoubleGene(param.name, min = param.minValue?.toDouble(), max = param.maxValue?.toDouble())
            RPCSupportedDataType.P_FLOAT, RPCSupportedDataType.FLOAT -> FloatGene(param.name, min = param.minValue?.toFloat(), max = param.maxValue?.toFloat())
            RPCSupportedDataType.P_LONG, RPCSupportedDataType.LONG -> LongGene(param.name, min = param.minValue, max = param.maxValue)
            RPCSupportedDataType.P_SHORT, RPCSupportedDataType.SHORT -> IntegerGene(param.name, min = param.minValue?.toInt()?:Short.MIN_VALUE.toInt(), max = param.maxValue?.toInt()?:Short.MAX_VALUE.toInt())
            RPCSupportedDataType.P_BYTE, RPCSupportedDataType.BYTE -> IntegerGene(param.name, min = param.minValue?.toInt()?:Byte.MIN_VALUE.toInt(), max = param.maxValue?.toInt()?:Byte.MAX_VALUE.toInt())
            RPCSupportedDataType.STRING, RPCSupportedDataType.BYTEBUFFER -> StringGene(param.name).apply {
                if (param.minValue != null || param.maxValue != null){
                    // add specification based on constraint info
                    specializationGenes.add(LongGene(param.name, min=param.minValue, max = param.maxValue))
                }
            }
            RPCSupportedDataType.ENUM -> handleEnumParam(param)
            RPCSupportedDataType.ARRAY, RPCSupportedDataType.SET, RPCSupportedDataType.LIST-> handleCollectionParam(param)
            RPCSupportedDataType.MAP -> handleMapParam(param)
            RPCSupportedDataType.UTIL_DATE -> handleUtilDate(param)
            RPCSupportedDataType.CUSTOM_OBJECT -> handleObjectParam(param)
            RPCSupportedDataType.CUSTOM_CYCLE_OBJECT -> CycleObjectGene(param.name)
            RPCSupportedDataType.PAIR -> throw IllegalStateException("ERROR: pair should be handled inside Map")
        }

        if (param.candidates != null){
            val candidates = param.candidates.map {p-> gene.copy().apply { setGeneBasedOnParamDto(this, p) } }.toList()
            if (candidates.isNotEmpty()){
                if (param.candidateReferences != null){
                    Lazy.assert { param.candidates.size == param.candidateReferences.size }
                    candidates.forEachIndexed { index, g ->  g.name = param.candidateReferences[index] }
                }
                val seededGene = handleGeneWithCandidateAsEnumGene(gene, candidates)

                if (param.candidateReferences == null)
                    return wrapWithOptionalGene(seededGene, param.isNullable)

                return DisruptiveGene(param.name, seededGene, 0.0)
            }
        }

        return wrapWithOptionalGene(gene, param.isNullable)
    }

    private fun handleGeneWithCandidateAsEnumGene(gene: Gene, candidates: List<Gene>) : SeededGene<*>{
        return  when (gene) {
            is StringGene -> SeededGene(gene.name, gene, EnumGene(gene.name, candidates.map { it as StringGene }))
            is IntegerGene -> SeededGene(gene.name, gene, EnumGene(gene.name, candidates.map { it as IntegerGene }))
            is FloatGene ->  SeededGene(gene.name, gene, EnumGene(gene.name, candidates.map { it as FloatGene }))
            is LongGene ->  SeededGene(gene.name, gene, EnumGene(gene.name, candidates.map { it as LongGene }))
            is DoubleGene -> SeededGene(gene.name, gene, EnumGene(gene.name, candidates.map { it as DoubleGene }))
            // might be DateGene
            else -> {
                throw IllegalStateException("Do not support configuring candidates for ${gene::class.java.simpleName} gene type")
            }
        }
    }

    private fun getValueForSeededGene(gene: SeededGene<*>) : String{
        return when (val pGene = gene.getPhenotype()) {
            is StringGene -> pGene.getValueAsRawString()
            is IntegerGene -> pGene.value.toString()
            is FloatGene -> pGene.value.toString()
            is LongGene -> pGene.value.toString()
            is DoubleGene -> pGene.value.toString()
            else -> {
                throw IllegalStateException("Do not support configuring candidates for ${gene::class.java.simpleName} gene type")
            }
        }
    }

    private fun handleUtilDate(param: ParamDto) : DateTimeGene{
        /*
            only support simple format (more details see [org.evomaster.client.java.controller.problem.rpc.schema.types.DateType]) for the moment
         */
        Lazy.assert { param.innerContent.size == 6 }
        return DateTimeGene(param.name)
    }

    private fun wrapWithOptionalGene(gene: Gene, isOptional: Boolean): Gene{
        return if (isOptional && gene !is OptionalGene) OptionalGene(gene.name, gene) else gene
    }

    private fun handleEnumParam(param: ParamDto): Gene{
        if (param.type.fixedItems.isNullOrEmpty()){
            LoggingUtil.uniqueWarn(log, "Enum with name (${param.type.fullTypeName}) has empty items")
            // TODO check not sure
            //return MapGene(param.type.fullTypeName, PairGene.createStringPairGene(StringGene( "NO_ITEM")), maxSize = 0)
            return EnumGene(param.name, listOf<String>())
        }
        return EnumGene(param.name, param.type.fixedItems.toList())

    }

    private fun handleMapParam(param: ParamDto) : Gene{
        val pair = param.type.example
        Lazy.assert { pair.innerContent.size == 2 }
        val keyTemplate = handleDtoParam(pair.innerContent[0])
        val valueTemplate = handleDtoParam(pair.innerContent[1])

        return MapGene(param.name, keyTemplate, valueTemplate, maxSize = param.maxSize?.toInt(), minSize = param.minSize?.toInt())
    }

    private fun handleCollectionParam(param: ParamDto) : Gene{
        val templateParam = when(param.type.type){
            RPCSupportedDataType.ARRAY, RPCSupportedDataType.SET, RPCSupportedDataType.LIST -> param.type.example
            else -> throw IllegalStateException("do not support the collection type: "+ param.type.type)
        }
        val template = handleDtoParam(templateParam)
        return ArrayGene(param.name, template, maxSize = param.maxSize?.toInt(), minSize = param.minSize?.toInt())
    }

    private fun handleObjectType(type: ParamDto): Gene{
        val typeName = type.type.fullTypeName
        if (type.innerContent.isEmpty()){
            LoggingUtil.uniqueWarn(log, "Object with name (${type.type.fullTypeName}) has empty fields")
            //return MapGene(typeName, PairGene.createStringPairGene(StringGene( "field"), isFixedFirst = true))
            return ObjectGene(typeName, listOf(), refType = typeName)
        }

        val fields = type.innerContent.map { f-> handleDtoParam(f) }

        return ObjectGene(typeName, fields, refType = typeName)
    }

    private fun handleObjectParam(param: ParamDto): Gene{
        val objType = typeCache[param.type.fullTypeName]
            ?:throw IllegalStateException("missing ${param.type.fullTypeName} in typeCache")
        return objType.copy().apply { this.name = param.name }
    }

}