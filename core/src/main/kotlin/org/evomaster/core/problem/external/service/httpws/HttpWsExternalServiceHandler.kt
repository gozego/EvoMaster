package org.evomaster.core.problem.external.service.httpws

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer
import com.google.inject.Inject
import org.evomaster.client.java.instrumentation.shared.ExternalServiceSharedUtils.isDefaultSignature
import org.evomaster.core.EMConfig
import org.evomaster.core.logging.LoggingUtil
import org.evomaster.core.problem.external.service.httpws.HttpWsExternalServiceUtils.generateRandomIPAddress
import org.evomaster.core.problem.external.service.httpws.HttpWsExternalServiceUtils.isAddressAvailable
import org.evomaster.core.problem.external.service.httpws.HttpWsExternalServiceUtils.isReservedIP
import org.evomaster.core.problem.external.service.httpws.HttpWsExternalServiceUtils.nextIPAddress
import org.evomaster.core.problem.external.service.httpws.param.HttpWsResponseParam
import org.evomaster.core.problem.external.service.param.ResponseParam
import org.evomaster.core.search.service.Randomness
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.annotation.PostConstruct

/**
 * To manage the external service related activities
 *
 * might create a superclass for external service handler
 */
class HttpWsExternalServiceHandler {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(HttpWsExternalServiceHandler::class.java)
    }

    /**
     * This will hold the information about the external service
     * calls inside the SUT. Information will be passed to the core
     * through AdditionalInfoDto and will be captured under
     * AbstractRestFitness and AbstractRestSample for further use.
     *
     * TODO: This is not the final implementation need to refactor but
     *  the concept is working.
     */

    @Inject
    private lateinit var randomness: Randomness

    @Inject
    private lateinit var config: EMConfig


    /**
     * Contains the information about [HttpWsExternalService] as map.
     *
     * Mapped against to signature of the [HttpWsExternalService].
     */
    private val externalServices: MutableMap<String, HttpWsExternalService> = mutableMapOf()


    /**
     * Contains last used loopback address for reference when creating
     * a new address
     */
    private var lastIPAddress: String = ""

    private var counter: Long = 0

    /**
     * whether the fake WM is initialized that the SUT will connect for the first time
     */
    private var isDefaultInitialized = false


    @PostConstruct
    fun initialize() {
        log.debug("Initializing {}", HttpWsExternalServiceHandler::class.simpleName)
        initDefaultWM()
    }



    /**
     * init default WM
     */
    private fun initDefaultWM(){
        if (config.externalServiceIPSelectionStrategy != EMConfig.ExternalServiceIPSelectionStrategy.NONE){
            if (!isDefaultInitialized){
                registerHttpExternalServiceInfo(DefaultHttpExternalServiceInfo.createDefaultHttps())
                registerHttpExternalServiceInfo(DefaultHttpExternalServiceInfo.createDefaultHttp())
                isDefaultInitialized = true
            }
        }
    }

    /**
     * This will allow adding ExternalServiceInfo to the Collection.
     *
     * If there is a WireMock instance is available for the [HttpWsExternalService] signature,
     * it will be skipped from creating a new one.
     */
    fun addExternalService(externalServiceInfo: HttpExternalServiceInfo) {
        if (config.externalServiceIPSelectionStrategy != EMConfig.ExternalServiceIPSelectionStrategy.NONE) {
            registerHttpExternalServiceInfo(externalServiceInfo)
        }
    }

    private fun registerHttpExternalServiceInfo(externalServiceInfo : HttpExternalServiceInfo){
        if (!externalServices.containsKey(externalServiceInfo.signature())) {
            val ip = getIP(externalServiceInfo.remotePort)
            lastIPAddress = ip
            externalServices[externalServiceInfo.signature()] = initWireMockServer(ip, externalServiceInfo)
        }
    }

    fun getExternalServiceMappings(): Map<String, String> {
        return externalServices.mapValues { it.value.getWireMockAddress() }
    }

    /**
     * Will return the next available IP address from the last know IP address
     * used for external service.
     */
    private fun getNextAvailableAddress(port: Int): String {
        val nextAddress: String = nextIPAddress(lastIPAddress)

        if (isAddressAvailable(nextAddress, port)) {
            return nextAddress
        } else {
            throw IllegalStateException(nextAddress.plus(" is not available for use"))
        }
    }

    /**
     * Will generate random IP address within the loopback range
     * while checking the availability. If not available will
     * generate a new one.
     */
    private fun generateRandomAvailableAddress(port: Int): String {
        val ip = generateRandomIPAddress(randomness)
        if (isAddressAvailable(ip, port)) {
            return ip
        }
        return generateRandomAvailableAddress(port)
    }

    fun getExternalServices(): Map<String, HttpWsExternalService> {
        return externalServices
    }

    fun reset() {
        externalServices.forEach {
            it.value.stopWireMockServer()
        }
    }

    /**
     * Reset all the served requests.
     * The WireMock instances will still be up and running
     */
    fun resetServedRequests() {
        externalServices.forEach { it.value.resetServedRequests() }
    }

    /**
     * Creates an [HttpExternalServiceAction] based on the given [HttpExternalServiceRequest]
     */
    fun createExternalServiceAction(request: HttpExternalServiceRequest, responseParam: HttpWsResponseParam?): HttpExternalServiceAction {
        val externalService = getExternalService(request.wireMockSignature)

        val action = if (responseParam == null)
            HttpExternalServiceAction(request, "", externalService, counter++).apply {
                doInitialize(randomness)
            }
        else
            HttpExternalServiceAction(request = request, response= responseParam, externalService =  externalService, id = counter++).apply {
                seeTopGenes().forEach { g-> g.markAllAsInitialized() }
            }

        return action
    }

    /**
     * Returns the [HttpWsExternalService] if the signature exists
     */
    fun getExternalService(signature: String): HttpWsExternalService {
        return externalServices.getValue(signature)
    }

    /**
     * Returns a list of the served requests related to the specific WireMock
     * as [HttpExternalServiceRequest]
     */
    fun getAllServedExternalServiceRequests(): List<HttpExternalServiceRequest> {
        return externalServices.values.filter { !isDefaultSignature(it.getSignature()) }.flatMap { it.getAllServedRequests() }
    }

    /**
     * @return a list of the served requests to the default WM
     */
    fun getAllServedRequestsToDefaultWM(): List<HttpExternalServiceRequest> {
        return externalServices.values.filter { isDefaultSignature(it.getSignature()) }.flatMap { it.getAllServedRequests() }
    }

    /**
     * Default IP address will be a randomly generated IP
     *
     * If user provided IP address isn't available on the port
     * IllegalStateException will be thrown.
     */
    private fun getIP(port: Int): String {
        val ip: String
        when (config.externalServiceIPSelectionStrategy) {
            // Although the default address will be a random, this
            // option allows selecting explicitly
            EMConfig.ExternalServiceIPSelectionStrategy.RANDOM -> {
                ip = if (externalServices.isNotEmpty()) {
                    getNextAvailableAddress(port)
                } else {
                    generateRandomAvailableAddress(port)
                }
            }
            EMConfig.ExternalServiceIPSelectionStrategy.USER -> {
                ip = if (externalServices.isNotEmpty()) {
                    getNextAvailableAddress(port)
                } else {
                    if (!isReservedIP(config.externalServiceIP)) {
                        if (isAddressAvailable(config.externalServiceIP, port)) {
                            config.externalServiceIP
                        } else {
                            throw IllegalStateException("User provided IP address is not available: ${config.externalServiceIP}:$port")
                        }
                    } else {
                        throw IllegalStateException("Can not use a reserved IP address: ${config.externalServiceIP}")
                    }
                }
            }
            else -> {
                ip = if (externalServices.isNotEmpty()) {
                    getNextAvailableAddress(port)
                } else {
                    generateRandomAvailableAddress(port)
                }
            }
        }
        return ip
    }

    /**
     * Will initialise WireMock instance on a given IP address for a given port.
     */
    private fun initWireMockServer(address: String, info: HttpExternalServiceInfo): HttpWsExternalService {
        val port = info.remotePort

        // TODO: Port need to be changed to the remote service port
        //  In CI also using remote ports as 80 and 443 fails
        val config =  WireMockConfiguration()
            .bindAddress(address)
            .extensions(ResponseTemplateTransformer(false))

        if (!info.isHttp() && !info.isHttps())
            LoggingUtil.uniqueWarn(log, "do not get explicit protocol for address ($address)")
        val applyHttps = info.isHttps() || (!info.isHttp() && info.isDerivedHttps())

        if (applyHttps){
            config.httpsPort(port)
        }else {
            config.port(port)
        }

        val wm = WireMockServer(config)
        val es = HttpWsExternalService(info, wm)

        /*
            for SUT, its first connection is re-directed to a given ip address,
            to keep same behavior in generated tests, we do not start the WM accordingly
         */
        if (info !is DefaultHttpExternalServiceInfo){
            wm.start()
            wireMockSetDefaults(es)
        }

        return es
    }



    /**
     * To prevent from the 404 when no matching stub below stub is added
     * WireMock throws an exception when there is no stub for the request
     * to avoid the exception it handled manually
     */
    private fun wireMockSetDefaults(es: HttpWsExternalService) {

        es.getWireMockServer().stubFor(es.getDefaultWMMappingBuilder())
    }


}