package org.evomaster.core.problem.rest

import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.parser.OpenAPIParser
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.Parameter
import org.evomaster.client.java.instrumentation.shared.ClassToSchemaUtils.OPENAPI_COMPONENT_NAME
import org.evomaster.client.java.instrumentation.shared.ClassToSchemaUtils.OPENAPI_SCHEMA_NAME
import org.evomaster.core.Lazy
import org.evomaster.core.logging.LoggingUtil
import org.evomaster.core.parser.RegexHandler
import org.evomaster.core.problem.api.service.param.Param
import org.evomaster.core.problem.rest.param.*
import org.evomaster.core.remote.SutProblemException
import org.evomaster.core.search.Action
import org.evomaster.core.search.gene.*
import org.evomaster.core.search.gene.collection.ArrayGene
import org.evomaster.core.search.gene.collection.EnumGene
import org.evomaster.core.search.gene.collection.FixedMapGene
import org.evomaster.core.search.gene.collection.PairGene
import org.evomaster.core.search.gene.datetime.DateGene
import org.evomaster.core.search.gene.datetime.DateTimeGene
import org.evomaster.core.search.gene.numeric.DoubleGene
import org.evomaster.core.search.gene.numeric.FloatGene
import org.evomaster.core.search.gene.numeric.IntegerGene
import org.evomaster.core.search.gene.numeric.LongGene
import org.evomaster.core.search.gene.optional.CustomMutationRateGene
import org.evomaster.core.search.gene.optional.OptionalGene
import org.evomaster.core.search.gene.placeholder.CycleObjectGene
import org.evomaster.core.search.gene.placeholder.LimitObjectGene
import org.evomaster.core.search.gene.string.Base64StringGene
import org.evomaster.core.search.gene.string.StringGene
import org.evomaster.core.search.gene.utils.GeneUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * https://github.com/OAI/OpenAPI-Specification/blob/3.0.1/versions/3.0.1.md
 *
 *  Create actions from a OpenApi/Swagger schema.
 *  Must support both V2 and V3 specs.
 *
 */
object RestActionBuilderV3 {

    private val log: Logger = LoggerFactory.getLogger(RestActionBuilderV3::class.java)
    private val idGenerator = AtomicInteger()

    private val refCache = mutableMapOf<String, Gene>()

    /**
     * Key -> schema in the form "name: {...}"
     * Value -> object gene for it
     */
    private val dtoCache = mutableMapOf<String, Gene>()

    private val mapper = ObjectMapper()


    /**
     * @param doParseDescription presents whether apply name/text analysis on description and summary of rest action
     */
    fun addActionsFromSwagger(swagger: OpenAPI,
                              actionCluster: MutableMap<String, Action>,
                              endpointsToSkip: List<String> = listOf(),
                              doParseDescription: Boolean = false) {

        actionCluster.clear()
        refCache.clear()
        dtoCache.clear()

        val skipped = mutableListOf<String>()
        val errorEndpoints = mutableListOf<String>()

        val basePath = getBasePathFromURL(swagger)

        swagger.paths
                .filter { e ->
                    if (endpointsToSkip.contains(e.key)) {
                        skipped.add(e.key)
                        false
                    } else {
                        true
                    }
                }
                .forEach { e ->

                    /*
                        In V2 there that a "host" and "basePath".
                        In V3, this was replaced by a "servers" list of URLs.
                        The "paths" are then appended to such URLs, which works
                        like a "host+basePath"
                     */

                    val restPath = RestPath(if (basePath == "/") e.key else (basePath + e.key))

                    if (e.value.`$ref` != null) {
                        //TODO
                        log.warn("Currently cannot handle \$ref: ${e.value.`$ref`}")
                    }

                    if (e.value.parameters != null && e.value.parameters.isNotEmpty()) {
                        //TODO
                        log.warn("Currently cannot handle 'path-scope' parameters")
                    }

                    if (!e.value.description.isNullOrBlank()) {
                        //TODO should we do something with it for doParseDescription?
                    }

                    if (e.value.get != null) handleOperation(actionCluster, HttpVerb.GET, restPath, e.value.get, swagger, doParseDescription, errorEndpoints)
                    if (e.value.post != null) handleOperation(actionCluster, HttpVerb.POST, restPath, e.value.post, swagger, doParseDescription, errorEndpoints)
                    if (e.value.put != null) handleOperation(actionCluster, HttpVerb.PUT, restPath, e.value.put, swagger, doParseDescription, errorEndpoints)
                    if (e.value.patch != null) handleOperation(actionCluster, HttpVerb.PATCH, restPath, e.value.patch, swagger, doParseDescription, errorEndpoints)
                    if (e.value.options != null) handleOperation(actionCluster, HttpVerb.OPTIONS, restPath, e.value.options, swagger, doParseDescription, errorEndpoints)
                    if (e.value.delete != null) handleOperation(actionCluster, HttpVerb.DELETE, restPath, e.value.delete, swagger, doParseDescription, errorEndpoints)
                    if (e.value.trace != null) handleOperation(actionCluster, HttpVerb.TRACE, restPath, e.value.trace, swagger, doParseDescription, errorEndpoints)
                    if (e.value.head != null) handleOperation(actionCluster, HttpVerb.HEAD, restPath, e.value.head, swagger, doParseDescription, errorEndpoints)
                }

        checkSkipped(skipped, endpointsToSkip, actionCluster, errorEndpoints)
    }

    /**
     * @param name of the Dto to parse
     * @param allSchemas contains all schemas of dto and its ref classes in the form
     *      "name: { "type name": "schema", "ref name": "schema", .... }"
     * @return a gene of the dto
     */
    fun createObjectGenesForDTOs(name: String, allSchemas: String) : Gene{
        if(!allSchemas.startsWith("\"$name\"")){
            throw IllegalArgumentException("Invalid name $name for schema $allSchemas")
        }

        val allSchemasValue = allSchemas.substring(1 + name.length + 2)

        val schemas = getMapStringFromSchemas(allSchemasValue)
        val dtoSchema = schemas[name] ?: throw IllegalStateException("cannot find the schema with $name from $allSchemas")

        if(dtoCache.containsKey(dtoSchema)){
            return dtoCache[dtoSchema]!!.copy()
        }

        val schema = """
            {
                "openapi": "3.0.0",
                "$OPENAPI_COMPONENT_NAME": {
                    "$OPENAPI_SCHEMA_NAME": $allSchemasValue
                }
            }          
        """.trimIndent()

        val swagger = OpenAPIParser().readContents(schema,null,null).openAPI


        schemas.forEach { (t, u) ->
            val gene = createObjectGene(t, swagger.components.schemas[t]!!,swagger, ArrayDeque(), t)
            dtoCache[u] = gene
        }

        return dtoCache[dtoSchema]!!.copy()
    }

    /**
     *
     */
    fun createObjectGeneForDTO(name: String, dtoSchema: String, referenceTypeName: String?) : Gene{

        if(! dtoSchema.startsWith("\"$name\"")){
            throw IllegalArgumentException("Invalid name $name for schema $dtoSchema")
        }

        if(dtoCache.containsKey(dtoSchema)){
            return dtoCache[dtoSchema]!!.copy()
        }

        //Note to simplify code, we just create a whole OpenAPI schema
        val schema = """
            {
                "openapi": "3.0.0",
                "$OPENAPI_COMPONENT_NAME": {
                    "$OPENAPI_SCHEMA_NAME": {
                        $dtoSchema
                    }
                }
            }          
        """.trimIndent()

        val swagger = OpenAPIParser().readContents(schema,null,null).openAPI
        val gene = createObjectGene(name, swagger.components.schemas[name]!!,swagger, ArrayDeque(), referenceTypeName)
        dtoCache[dtoSchema] = gene
        return gene.copy()
    }

    fun createObjectGeneForDTOs(names: List<String>, dtoSchemas: List<String>, referenceTypeNames: List<String?>) : List<Gene>{
        Lazy.assert { names.size == dtoSchemas.size }

        dtoSchemas.forEachIndexed { index, s ->
            if(! s.startsWith("\"${names[index]}\"")){
                throw IllegalArgumentException("Invalid name ${names[index]} for schema $s")
            }
        }


        val unidentified = dtoSchemas.mapIndexed { index, s -> index to s  }.filter{ !dtoCache.containsKey(it.second) }

        //Note to simplify code, we just create a whole OpenAPI schema
        val schema = """
            {
                "openapi": "3.0.0",
                "$OPENAPI_COMPONENT_NAME": {
                    "$OPENAPI_SCHEMA_NAME": {
                        ${unidentified.joinToString(",") { it.second }}
                    }
                }
            }          
        """.trimIndent()

        val swagger = OpenAPIParser().readContents(schema,null,null).openAPI
        unidentified.forEach {s->
            val gene = getGene(names[s.first], swagger.components.schemas[names[s.first]]!!,swagger, ArrayDeque(), referenceTypeNames[s.first])
            if (!dtoCache.containsKey(s.second))
                dtoCache[s.second] = gene
        }

        return dtoSchemas.map { dtoCache[it]!!.copy() }
    }


    private fun handleOperation(
            actionCluster: MutableMap<String, Action>,
            verb: HttpVerb,
            restPath: RestPath,
            operation: Operation,
            swagger: OpenAPI,
            doParseDescription: Boolean,
            errorEndpoints : MutableList<String> = mutableListOf()
    ) {

        try{
            val params = extractParams(verb, restPath, operation, swagger)
            repairParams(params, restPath)

            val produces = operation.responses?.values //different response objects based on HTTP code
                ?.filter { it.content != null && it.content.isNotEmpty() }
                //each response can have different media-types
                ?.flatMap { it.content.keys }
                ?.toSet() // remove duplicates
                ?.toList()
                ?: listOf()

            val actionId = "$verb$restPath${idGenerator.incrementAndGet()}"
            val action = RestCallAction(actionId, verb, restPath, params, produces = produces)

            //TODO update for new parser
//                        /*This section collects information regarding the types of data that are
//                        used in the response of an action (if such data references are provided in the
//                        swagger definition
//                        */
//                        val responses = o.value.responses.filter { it.value.responseSchema != null }
//
//                        if (responses.isNotEmpty()) {
//                            responses.filter { it.value.responseSchema is RefModel }.forEach { (k, v) ->
//                                action.addRef(k, (v.responseSchema as RefModel).simpleRef)
//                            }
//                        }

            if (doParseDescription) {
                var info = operation.description
                if (!info.isNullOrBlank() && !info.endsWith(".")) info += "."
                if (!operation.summary.isNullOrBlank()) info = if (info == null) operation.summary else (info + " " + operation.summary)
                if (!info.isNullOrBlank() && !info.endsWith(".")) info += "."
                action.initTokens(info)
            }

            actionCluster[action.getName()] = action
        }catch (e: Exception){
            log.warn("Fail to parse endpoint $verb$restPath due to "+e.message)
            errorEndpoints.add("$verb$restPath")
        }

    }


    private fun extractParams(
            verb: HttpVerb,
            restPath: RestPath,
            operation: Operation,
            swagger: OpenAPI
    ): MutableList<Param> {

        val params = mutableListOf<Param>()

        removeDuplicatedParams(operation)
                .forEach { p ->

                    if(p.`$ref` != null){
                        val param = getLocalParameter(swagger, p.`$ref`)
                        if(param == null){
                            log.warn("Failed to handle: ${p.`$ref`}")
                        } else {
                            handleParam(param, swagger, params)
                        }
                    } else {
                        handleParam(p, swagger, params)
                    }
                }

        handleBodyPayload(operation, verb, restPath, swagger, params)

        return params
    }

    private fun handleParam(p: Parameter, swagger: OpenAPI, params: MutableList<Param>) {
        val name = p.name ?: "undefined"

        if(p.schema == null){
            log.warn("No schema definition for parameter $name")
            return
        }

        var gene = getGene(name, p.schema, swagger, referenceClassDef = null)

        if (p.`in` == "path" && gene is StringGene) {
            /*
                            We want to avoid empty paths, and special chars like / which
                            would lead to 2 variables, or any other char that does affect the
                            structure of the URL, like '.'
                         */
            gene = StringGene(gene.name, gene.value, 1, gene.maxLength, listOf('/', '.'))
        }

        if (p.required != true && p.`in` != "path" && gene !is OptionalGene) {
            // As of V3, "path" parameters must be required
            gene = OptionalGene(name, gene)
        }

        //TODO could exploit "x-example" if available in OpenApi

        when (p.`in`) {
            "query" -> params.add(QueryParam(name, gene))
            /*
                a path is inside a Disruptive Gene, because there are cases in which we want to prevent
                mutation. Note that 1.0 means can always be mutated
             */
            "path" -> params.add(PathParam(name, CustomMutationRateGene("d_", gene, 1.0)))
            "header" -> params.add(HeaderParam(name, gene))
            "cookie" -> params // do nothing?
            //TODO "cookie" does it need any special treatment? as anyway handled in auth configs
            else -> throw IllegalStateException("Unrecognized: ${p.getIn()}")
        }
    }

    /**
     * Have seen some cases of (old?) Swagger wrongly marking path params as query params
     */
    private fun repairParams(params: MutableList<Param>, restPath: RestPath) {

        restPath.getVariableNames().forEach { n ->

            val p = params.find { p -> p is PathParam && p.name == n }
            if (p == null) {
                log.warn("No path parameter for variable '$n' in $restPath")

                //this could happen if bug in Swagger
                var fixed = false
                for (i in 0 until params.size) {
                    if (params[i] is QueryParam && params[i].name == n) {
                        params[i] = PathParam(params[i].name, CustomMutationRateGene("d_", params[i].gene, 1.0))
                        fixed = true
                        break
                    }
                }

                if (!fixed) {
                    //just create a string
                    val k = PathParam(n, CustomMutationRateGene("d_", StringGene(n), 1.0))
                    params.add(k)
                }
            }
        }
    }

    private fun handleBodyPayload(
            operation: Operation,
            verb: HttpVerb,
            restPath: RestPath,
            swagger: OpenAPI,
            params: MutableList<Param>) {

        if (operation.requestBody == null) {
            return
        }

        if (!listOf(HttpVerb.POST, HttpVerb.PATCH, HttpVerb.PUT).contains(verb)) {
            log.warn("In HTTP, body payloads are undefined for $verb")
            return
        }

        val body = operation.requestBody!!

        val name = "body"

        val bodies = body.content.filter {
            /*
                If it is a reference, then it must be present.
                Had issue with SpringFox in Proxyprint generating wrong schemas
                when WebRequest and Principal are used
             */
            if (it.value.schema == null) {
                false
            } else {
                val reference = it.value.schema.`$ref`
                reference.isNullOrBlank() || getLocalObjectSchema(swagger, reference) != null
            }
        }

        if (bodies.isEmpty()) {
            log.warn("No valid body-payload for $verb:$restPath")
            /*
                This will/should be handled by Testability Transformations at runtime.
                So we just default to a string map
             */
            return
        }


        /*
            FIXME as of V3, different types might have different body definitions.
            This should refactored to enable possibility of different BodyParams
        */
        val obj: MediaType = bodies.values.first()
        var gene = getGene("body", obj.schema, swagger, referenceClassDef = null)


        if (body.required != true && gene !is OptionalGene) {
            gene = OptionalGene(name, gene)
        }

        val contentTypeGene = EnumGene<String>("contentType", bodies.keys)

        params.add(BodyParam(gene, contentTypeGene))
    }

    private fun possiblyOptional(gene: Gene, required: Boolean?): Gene {

        if (required != true) {
            return OptionalGene(gene.name, gene).also { GeneUtils.preventCycles(it) }
        }

        return gene
    }

    private fun getGene(
            name: String,
            schema: Schema<*>,
            swagger: OpenAPI,
            history: Deque<String> = ArrayDeque<String>(),
            referenceClassDef: String?
    ): Gene {

        if (!schema.`$ref`.isNullOrBlank()) {
            return createObjectFromReference(name, schema.`$ref`, swagger, history)
        }


        /*
            https://github.com/OAI/OpenAPI-Specification/blob/3.0.1/versions/3.0.1.md#dataTypeFormat

        Common Name	    type	format	Comments
        integer	        integer	int32	signed 32 bits
        long	        integer	int64	signed 64 bits
        float	        number	float
        double	        number	double
        string	        string
        byte	        string	byte	base64 encoded characters
        binary	        string	binary	any sequence of octets
        boolean	        boolean
        date	        string	date	As defined by full-date - RFC3339
        dateTime	    string	date-time	As defined by date-time - RFC3339
        password	    string	password	Used to hint UIs the input needs to be obscured.
         */

        val type = schema.type
        val format = schema.format

        if (schema.enum?.isNotEmpty() == true) {

            //Besides the defined values, add one to test robustness
            when (type) {
                "string" ->
                    return EnumGene(name, (schema.enum as MutableList<String>).apply { add("EVOMASTER") })
                /*
                    Looks like a possible bug in the parser, where numeric enums can be read as strings... got this
                    issue in GitLab schemas, eg for visibility_level
                 */
                "integer" -> {
                    if (format == "int64") {
                        val data : MutableList<Long> = schema.enum
                                .map{ if(it is String) it.toLong() else it as Long}
                                .toMutableList()

                        return EnumGene(name, (data).apply { add(42L) })
                    }

                    val data : MutableList<Int> = schema.enum
                            .map{ if(it is String) it.toInt() else it as Int}
                            .toMutableList()
                    return EnumGene(name, data.apply { add(42) })
                }
                "number" -> {
                    //if (format == "double" || format == "float") {
                    //TODO: Is it always casted as Double even for Float??? Need test
                    val data : MutableList<Double> = schema.enum
                            .map{ if(it is String) it.toDouble() else it as Double}
                            .toMutableList()
                    return EnumGene(name, data.apply { add(42.0) })
                }
                else -> log.warn("Cannot handle enum of type: $type")
            }
        }

        /*
            TODO constraints like min/max
         */

        //first check for "optional" format
        when (format?.lowercase()) {
            "int32" -> return IntegerGene(name)
            "int64" -> return LongGene(name)
            "double" -> return DoubleGene(name)
            "float" -> return FloatGene(name)
            "password" -> return StringGene(name) //nothing special to do, it is just a hint
            "binary" -> return StringGene(name) //does it need to be treated specially?
            "byte" -> return Base64StringGene(name)
            "date" -> return DateGene(name)
            "date-time" -> return DateTimeGene(name)
            else -> if (format != null) {
                LoggingUtil.uniqueWarn(log, "Unhandled format '$format'")
            }
        }

        /*
                If a format is not defined, the type should default to
                the JSON Schema definition
         */
        when (type?.lowercase()) {
            "integer" -> return IntegerGene(name)
            "number" -> return DoubleGene(name)
            "boolean" -> return BooleanGene(name)
            "string" -> {
                return if (schema.pattern == null) {
                    StringGene(name)
                } else {
                    try {
                        RegexHandler.createGeneForEcma262(schema.pattern).apply { this.name = name }
                    } catch (e: Exception) {
                        /*
                            TODO: if the Regex is syntactically invalid, we should warn
                            the user. But, as we do not support 100% regex, might be an issue
                            with EvoMaster. Anyway, in such cases, instead of crashing EM, let's just
                            take it as a String.
                            When 100% support, then tell user that it is his/her fault
                         */
                        LoggingUtil.uniqueWarn(log, "Cannot handle regex: ${schema.pattern}")
                        StringGene(name)
                    }
                }
            }
            "array" -> {
                if (schema is ArraySchema) {

                    val arrayType: Schema<*> = if (schema.items == null) {
                        LoggingUtil.uniqueWarn(log, "Array type '$name' is missing mandatory field 'items' to define its type." +
                                " Defaulting to 'string'")
                        Schema<Any>().also { it.type = "string" }
                    } else {
                        schema.items
                    }
                    val template = getGene(name + "_item", arrayType, swagger, history, referenceClassDef = null)

                    //Could still have an empty []
//                    if (template is CycleObjectGene) {
//                        return CycleObjectGene("<array> ${template.name}")
//                    }
                    return ArrayGene(name, template)
                } else {
                    LoggingUtil.uniqueWarn(log, "Invalid 'array' definition for '$name'")
                }
            }

            "object" -> {
                return createObjectGene(name, schema, swagger, history, referenceClassDef)
            }

            "file" -> return StringGene(name) //TODO file is a hack. I want to find a more elegant way of dealing with it (BMR)
        }

        if (name == "body" && schema.properties?.isNotEmpty() == true) {
            /*
                This could happen when parsing a body-payload as formData
            */
            return createObjectGene(name, schema, swagger, history, referenceClassDef)
        }

        if (type == null && format == null) {
            LoggingUtil.uniqueWarn(log, "No type/format information provided for '$name'. Defaulting to 'string'")
            return StringGene(name)
        }

        throw IllegalArgumentException("Cannot handle combination $type/$format")
    }

    /**
     * @param referenceTypeName is the name of object type
     */
    private fun createObjectGene(name: String, schema: Schema<*>, swagger: OpenAPI, history: Deque<String>, referenceTypeName: String?): Gene {

        val fields = schema.properties?.entries?.map {
            possiblyOptional(
                    getGene(it.key, it.value, swagger, history, referenceClassDef = null),
                    schema.required?.contains(it.key)
            )
        } ?: listOf()

        /*
                    Can be either a boolean or a Schema
                 */
        val additional = schema.additionalProperties

        if (additional is Boolean) {
            /*
                if 'false', no other fields besides the specified ones can be added.
                Default is 'true'.
              */
            //TODO could add extra fields for robustness testing
        }
        if (additional is Schema<*>) {

            /*
               TODO could add extra fields for robustness testing,
               with and without following the given schema for their type
             */

            /*
                TODO proper handling.
                Using a map is just a temp solution
             */

            if (fields.isEmpty()) {
                /*
                   support additionalProperties with ref
                */
                if (!additional.`$ref`.isNullOrBlank()) {
                    val valueTemplate = createObjectFromReference("valueTemplate", additional.`$ref`, swagger, history)
                    val pairTemplate = PairGene("template", StringGene("keyTemplate"), valueTemplate.copy())
                    return FixedMapGene(name, pairTemplate)
                }

                // here, the first of pairgene should not be mutable
                return FixedMapGene(name, PairGene.createStringPairGene(getGene(name + "_field", additional, swagger, history, null), isFixedFirst = true))
            }
        }

        //TODO allOf, anyOf, oneOf and not

        if (fields.isEmpty()) {
            LoggingUtil.uniqueWarn(log,"No fields for object definition: $name")
            // here, the first of pairgene should not be mutable
            return FixedMapGene(name, PairGene.createStringPairGene(StringGene(name + "_field"), isFixedFirst = true))
        }

        /*
            add refClass with title of SchemaObject
            Man: shall we pop history here?
         */
        return ObjectGene(name, fields, if(schema is ObjectSchema) referenceTypeName?:schema.title else null)
    }


    private fun createObjectFromReference(name: String,
                                          reference: String,
                                          swagger: OpenAPI,
                                          history: Deque<String> = ArrayDeque()
    ): Gene {

        /*
            The problem in caching objects is that their tree can depend on where they are mounted.
            For example, assume A->B  will not work (ie cycle) if mounted under another object that has
            B as ancestor, eg, D->C->B->X->A.
            An easy case in which this cannot happen is when the target object is a root, ie used directly
            in a parameter and not inside other objects. In such cases, we can cache it.
         */
        val isRoot = history.isEmpty()

        /*
            We need to avoid cycles like A.B.A...
            From root to leaves, how many repeated object should appear on a path?
            TODO Maybe this should be config to experiment with.
            Anyway, it is a problem in scout-api
         */
        val cycleDepth = 1

        if (history.count { it == reference } >= cycleDepth) {
            return CycleObjectGene("Cycle for: $reference")
        }

        if (isRoot && refCache.containsKey(reference)) {
            return refCache[reference]!!.copy()
        }

        /*
            TODO This could be customized in EMConfig
         */
        val depthLimit = 5
        if(history.size == depthLimit){
            return LimitObjectGene("Object-depth limit reached for: $reference")
        }

        try {
            URI(reference)
        } catch (e: URISyntaxException) {
            LoggingUtil.uniqueWarn(log, "Object reference is not a valid URI: $reference")
        }

        val schema = getLocalObjectSchema(swagger, reference)

        if (schema == null) {
            //token after last /
            val classDef = getClassDef(reference)

            LoggingUtil.uniqueWarn(log, "No $classDef among the object definitions in the OpenApi file")

            return ObjectGene(name, listOf(), classDef)
        }

        history.push(reference)

        val gene = getGene(name, schema, swagger, history, getClassDef(reference))

        if(isRoot) {
            GeneUtils.preventCycles(gene)
            GeneUtils.preventLimit(gene)
            refCache[reference] = gene
        }

        history.pop()

        return gene
    }

    private fun getClassDef(reference: String) = reference.substring(reference.lastIndexOf("/") + 1)

    private fun getLocalParameter(swagger: OpenAPI, reference: String) : Parameter?{
        val name = extractReferenceName(reference)

        return swagger.components.parameters[name]
    }

    private fun getLocalObjectSchema(swagger: OpenAPI, reference: String): Schema<*>? {

        val classDef = extractReferenceName(reference)

        return swagger.components.schemas[classDef]
    }

    private fun extractReferenceName(reference: String): String {
        try {
            URI(reference)
        } catch (e: URISyntaxException) {
            LoggingUtil.uniqueWarn(log, "Object reference is not a valid URI: $reference")
        }

        //token after last /
        return reference.substring(reference.lastIndexOf("/") + 1)
    }

    private fun removeDuplicatedParams(operation: Operation): List<Parameter> {

        /*
            Duplicates are not allowed, based on combination of "name" and "location".
            https://github.com/OAI/OpenAPI-Specification/blob/3.0.1/versions/3.0.1.md#operationObject
         */

        if (operation.parameters == null) {
            return listOf()
        }

        val selection = mutableListOf<Parameter>()
        val seen = mutableSetOf<String>()

        for (p in operation.parameters) {

            val key = p.`in` + "_" + p.name
            if (!seen.contains(key)) {
                seen.add(key)
                selection.add(p)
            }
        }

        val diff = operation.parameters.size - selection.size
        if (diff > 0) {
            log.warn("Operation ${operation.operationId} has $diff repeated parameters")
        }

        return selection
    }


    private fun checkSkipped(
        skipped: List<String>,
        endpointsToSkip: List<String>,
        actionCluster: Map<String, Action>,
        errorEndpoints: List<String>
    ) {
        if(endpointsToSkip.toSet().size != endpointsToSkip.size){
            throw SutProblemException("There are repeated, non-unique endpoint-to-skip declarations")
        }

        if (skipped.size != endpointsToSkip.size) {
            val msg = "${endpointsToSkip.size} were set to be skipped, but only ${skipped.size}" +
                    " were found in the schema"
            LoggingUtil.getInfoLogger().error(msg)
            endpointsToSkip.filter { !skipped.contains(it) }
                    .forEach { LoggingUtil.getInfoLogger().warn("Missing endpoint: $it") }
            throw SutProblemException(msg)
        }

        LoggingUtil.getInfoLogger().apply {
            if (skipped.isNotEmpty()) {
                info("Skipped ${skipped.size} path endpoints from the schema configuration")
            }

            val n = actionCluster.size
            when (n) {
                0 -> warn("There is _NO_ usable RESTful API endpoint defined in the schema configuration")
                1 -> info("There is only one usable RESTful API endpoint defined in the schema configuration")
                else -> info("There are $n usable RESTful API endpoints defined in the schema configuration")
            }

            if (errorEndpoints.isNotEmpty()){
                warn("There are ${errorEndpoints.size} endpoints which might have errors and would not be handled in the generation")
            }
        }
    }

    fun getModelsFromSwagger(swagger: OpenAPI,
                             modelCluster: MutableMap<String, ObjectGene>) {
        modelCluster.clear()

        /*
            needs to check whether there exist some side-effects
            if do not clean those, some testDeterminism might fail due to inconsistent warning log.
         */
        refCache.clear()
        dtoCache.clear()

        if (swagger.components?.schemas != null) {
            swagger.components.schemas
                    .forEach {
                        val model = createObjectFromReference(it.key,
                                it.component1(),
                                swagger
                        )
                        when (model) {
                            //BMR: the modelCluster expects an ObjectGene. If the result is not that, it is wrapped in one.
                            is ObjectGene -> modelCluster.put(it.component1(), model)
                            //is MapGene<*, *> -> modelCluster.put(it.component1(), ObjectGene(it.component1(), listOf(model)))
                            //Andrea: this was wrong, as generating invalid genes where writing expectations.
                            // this is a tmp fix
                            is FixedMapGene<*, *> -> modelCluster.put(it.component1(), ObjectGene(it.component1(), listOf()))
                        }

                    }
        }
    }

    fun getBasePathFromURL(swagger: OpenAPI): String {
        /*
            TODO would need more general approach, as different HTTP servers could
            have different base paths
         */
        val serverUrl = swagger.servers[0].url
        val basePath: String = try {
            URI(serverUrl).path.trim()
        } catch (e: URISyntaxException) {
            LoggingUtil.uniqueWarn(log, "Invalid URI used in schema to define servers: $serverUrl")
            ""
        }
        return basePath
    }

    /**
     * build a rest action based on the given [url]
     */
    fun buildActionBasedOnUrl(baseUrl: String, id : String, verb: HttpVerb, url: String, skipOracleChecks : Boolean) : RestCallAction?{

        // if the url does not start with baseUrl (i.e., not from SUT), then there might be no point to execute this rest action
        if (!url.startsWith(baseUrl)) return null

        // fragments # are ignored when making HTTP calls
        val uri = URI(url.replaceAfter("#","").removeSuffix("#"))
//        Lazy.assert { "${uri.scheme}://${uri.host}:${uri.port}" == baseUrl }

        val path = RestPath("${uri.scheme}://${uri.host}:${uri.port}${uri.path}".removePrefix(baseUrl.removeSuffix("/")))
        val query : MutableList<Param> = uri.query?.split("&")?.map { q->
            val keyValue = q.split("=")
            if (keyValue.size == 2 && keyValue[0].isNotBlank())
                QueryParam(keyValue[0], StringGene(keyValue[0], keyValue[1]))
            else {
                /*
                    key-value pair is not restricted for query
                    eg, /v2/api-docs?foo is considered as valid
                    see https://datatracker.ietf.org/doc/html/rfc3986#section-3.4
                 */
                log.warn("Currently not supporting a GET RestAction with the url '$url' ," +
                        " as all query parameters should be in the form key=value")
                return null
            }
        }?.toMutableList()?: mutableListOf()
        return RestCallAction(id, verb, path, query, skipOracleChecks= skipOracleChecks)
    }

    private fun getMapStringFromSchemas(schemas: String) : Map<String, String>{
        val objs = mapper.readTree(schemas)
        val maps = mutableMapOf<String, String>()
        objs.fields().forEach { f->
            maps[f.key] = f.value.toString()
        }

        return maps
    }

}