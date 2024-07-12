package org.evomaster.core.output.auth

import org.evomaster.core.output.Lines
import org.evomaster.core.output.OutputFormat
import org.evomaster.core.output.service.ApiTestCaseWriter
import org.evomaster.core.output.service.HttpWsTestCaseWriter
import org.evomaster.core.problem.httpws.HttpWsAction
import org.evomaster.core.problem.httpws.auth.EndpointCallLogin
import org.evomaster.core.problem.rest.ContentType
import org.evomaster.core.search.EvaluatedIndividual
import org.evomaster.core.search.Individual

/**
 * A test case might need to get cookies to do authenticated requests.
 * This means we need to first do a login/signup call to get cookies,
 * and store them somewhere in a variable
 */
object CookieWriter {

    fun cookiesName(info: EndpointCallLogin): String = "cookies_${info.name}"

    /**
     *  Return the distinct auth info on cookie-based login in all actions
     *  of this individual
     */
    fun getCookieLoginAuth(ind: Individual) = ind.seeAllActions()
        .filterIsInstance<HttpWsAction>()
        .filter { it.auth.endpointCallLogin != null && it.auth.endpointCallLogin!!.expectsCookie() }
        .distinctBy { it.auth.name }
        .map { it.auth.endpointCallLogin!! }


    fun handleGettingCookies(
        format: OutputFormat,
        ind: EvaluatedIndividual<*>,
        lines: Lines,
        baseUrlOfSut: String,
        testCaseWriter: HttpWsTestCaseWriter
    ) {

        val cookiesInfo = getCookieLoginAuth(ind.individual)

        if (cookiesInfo.isNotEmpty()) {
            lines.addEmpty()
        }

        for (k in cookiesInfo) {

            when {
                format.isJava() -> lines.add("final Map<String,String> ${cookiesName(k)} = ")
                format.isKotlin() -> lines.add("val ${cookiesName(k)} : Map<String,String> = ")
                format.isJavaScript() -> lines.add("const ${cookiesName(k)} = (")
                //TODO Python
            }

            testCaseWriter.startRequest(lines)
            lines.indent()
            addCallCommand(lines, k, testCaseWriter, format, baseUrlOfSut, cookiesName(k))

            when {
                format.isJavaOrKotlin() -> lines.add(".then().extract().cookies()")
                format.isJavaScript() -> lines.add(").header['set-cookie'][0].split(';')[0]")
                format.isPython() -> lines.append(".cookies")
            }
            //TODO check response status and cookie headers?

            lines.appendSemicolon()
            lines.addEmpty()

            if (!format.isPython()) {
                lines.deindent()
            }
        }
    }

    fun addCallCommand(
        lines: Lines,
        k: EndpointCallLogin,
        testCaseWriter: HttpWsTestCaseWriter,
        format: OutputFormat,
        baseUrlOfSut: String,
        targetVariable: String
    ) {

        lines.add(".post(")
        if (k.externalEndpointURL != null) {
            lines.append("\"${k.externalEndpointURL}\"")
        } else {
            when{
                format.isJava() || format.isJavaScript() -> lines.append("$baseUrlOfSut + \"")
                format.isPython() -> lines.append("self.$baseUrlOfSut + \"")
                else -> lines.append("\"\${$baseUrlOfSut}")
            }
            lines.append("${k.endpoint}\"")
        }
        lines.append(")")


        when {
            format.isJavaOrKotlin() -> lines.add(".contentType(\"${k.contentType.defaultValue}\")")
            format.isJavaScript() -> lines.add(".set(\"content-type\", \"${k.contentType.defaultValue}\")")
            format.isPython() -> {
                lines.add("headers = {}")
                lines.add("headers[\"content-type\"] = \"${k.contentType.defaultValue}\"")
            }
        }

        when (k.contentType) {
            ContentType.X_WWW_FORM_URLENCODED -> {
                val send = testCaseWriter.sendBodyCommand()
                lines.add(".$send(\"${k.payload}\")")
            }
            ContentType.JSON -> {
                testCaseWriter.printSendJsonBody(k.payload, lines)
            }
            else -> {
                throw IllegalStateException("Currently not supporting yet ${k.contentType} in login")
            }
        }

        //TODO should check specified verb
        if (format.isPython()) {
            lines.add("$targetVariable = requests \\")
            lines.indent(2)
        }

        if (format.isPython()) {
            lines.append(", ")
            lines.indented {
                lines.add("headers=headers, data=body")
            }
            lines.deindent(2)
        }

    }
}
