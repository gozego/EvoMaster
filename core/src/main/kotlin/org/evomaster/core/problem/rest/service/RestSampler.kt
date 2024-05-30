package org.evomaster.core.problem.rest.service

import org.evomaster.core.Lazy
import org.evomaster.core.problem.enterprise.SampleType
import org.evomaster.core.problem.rest.*
import org.evomaster.core.problem.httpws.auth.HttpWsAuthenticationInfo
import org.evomaster.core.problem.httpws.auth.HttpWsNoAuth
import org.evomaster.core.problem.rest.param.PathParam
import org.evomaster.core.search.tracer.Traceable
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class RestSampler : AbstractRestSampler(){

    companion object {
        private val log: Logger = LoggerFactory.getLogger(RestSampler::class.java)
    }


    override fun sampleAtRandom(): RestIndividual {

        val actions = mutableListOf<RestCallAction>()
        val n = randomness.nextInt(1, getMaxTestSizeDuringSampler())

        (0 until n).forEach {
            actions.add(sampleRandomAction(0.05) as RestCallAction)
        }
        val ind = RestIndividual(actions, SampleType.RANDOM, mutableListOf(), this, time.evaluatedIndividuals)
        ind.doGlobalInitialize(searchGlobalState)
        return ind
    }



    override fun smartSample(): RestIndividual {

        /*
            At the beginning, sample from this set, until it is empty
         */
        if (adHocInitialIndividuals.isNotEmpty()) {
            return adHocInitialIndividuals.removeAt(adHocInitialIndividuals.size - 1)
        }

        if (getMaxTestSizeDuringSampler() <= 1) {
            /*
                Here we would have sequences of endpoint calls that are
                somehow linked to each other, eg a DELETE on a resource
                created with a POST.
                If can have only one call, then just go random
             */
            return sampleAtRandom()
        }


        val test = mutableListOf<RestCallAction>()

        val action = sampleRandomAction(0.0) as RestCallAction

        /*
            TODO: each of these "smart" tests could end with a GET, to make
            the test easier to read and verify the results (eg side-effects of
            DELETE/PUT/PATCH operations).
            But doing that as part of the tests could be inefficient (ie a lot
            of GET calls).
            Maybe that should be done as part of an "assertion generation" phase
            (which would also be useful for creating checks on returned JSONs)
         */

        val sampleType = when (action.verb) {
            HttpVerb.GET -> handleSmartGet(action, test)
            HttpVerb.POST -> handleSmartPost(action, test)
            HttpVerb.PUT -> handleSmartPut(action, test)
            HttpVerb.DELETE -> handleSmartDelete(action, test)
            HttpVerb.PATCH -> handleSmartPatch(action, test)
            else -> SampleType.RANDOM
        }

        if (test.isNotEmpty()) {
            val objInd = RestIndividual(test, sampleType, mutableListOf(),
                trackOperator = if (config.trackingEnabled()) this else null,
                index = if (config.trackingEnabled()) time.evaluatedIndividuals else Traceable.DEFAULT_INDEX)

            objInd.doGlobalInitialize(searchGlobalState)
            return objInd
        }

        return sampleAtRandom()
    }

    private fun handleSmartPost(post: RestCallAction, test: MutableList<RestCallAction>): SampleType {

        Lazy.assert{post.verb == HttpVerb.POST}

        //as POST is used in all the others, maybe here we do not really need to handle it specially?
        //we still do it, as might be some side-effects we have not thought about
        test.add(post)
        return SampleType.SMART
    }

    private fun handleSmartDelete(delete: RestCallAction, test: MutableList<RestCallAction>): SampleType {

        Lazy.assert{delete.verb == HttpVerb.DELETE}

        createWriteOperationAfterAPost(delete, test)

        return SampleType.SMART
    }

    private fun handleSmartPatch(patch: RestCallAction, test: MutableList<RestCallAction>): SampleType {

        Lazy.assert{patch.verb == HttpVerb.PATCH}

        createWriteOperationAfterAPost(patch, test)

        return SampleType.SMART
    }

    private fun handleSmartPut(put: RestCallAction, test: MutableList<RestCallAction>): SampleType {

        Lazy.assert{put.verb == HttpVerb.PUT}

        /*
            A PUT might be used to update an existing resource, or to create a new one
         */
        if (randomness.nextBoolean(0.2)) {
            /*
                with low prob., let's just try the PUT on its own.
                Recall we already add single calls on each endpoint at initialization
             */
            test.add(put)
            return SampleType.SMART
        }

        createWriteOperationAfterAPost(put, test)
        return SampleType.SMART
    }

    /**
     *    Only for PUT, DELETE, PATCH
     */
    private fun createWriteOperationAfterAPost(write: RestCallAction, test: MutableList<RestCallAction>) {

        Lazy.assert{write.verb == HttpVerb.PUT || write.verb == HttpVerb.DELETE || write.verb == HttpVerb.PATCH}

        test.add(write)

        //Need to find a POST on a parent collection resource
        if (test.size < getMaxTestSizeDuringSampler()) {
            builder.createResourcesFor(write, test)
        }

        if (write.verb == HttpVerb.PATCH &&
                getMaxTestSizeDuringSampler() >= test.size + 1 &&
                randomness.nextBoolean()) {
            /*
                As PATCH is not idempotent (in contrast to PUT), it can make sense to test
                two patches in sequence
             */
            val secondPatch = builder.createActionFor(write, write)
            test.add(secondPatch)
            secondPatch.locationId = write.locationId
        }

        test.forEach { t ->
            preventPathParamMutation(t)
        }
    }

    private fun handleSmartGet(get: RestCallAction, test: MutableList<RestCallAction>): SampleType {

        Lazy.assert{get.verb == HttpVerb.GET}

        /*
           A typical case is something like

           POST /elements
           GET  /elements/{id}

           Problems is that the {id} might not be known beforehand,
           eg it would be the result of calling POST first, where the
           path would be in the returned Location header.

           However, we might even encounter cases like:

           POST /elements/{id}
           GET  /elements/{id}

           which is possible, although bit weird, as in such case it
           would be better to have a PUT instead of a POST.

           Note: we prefer a POST to create a resource, as that is the
           most common case, and not all PUTs allow creation
         */

        test.add(get)

        val created = if (test.size >= getMaxTestSizeDuringSampler()) {
            false
        } else builder.createResourcesFor(get, test)

        if (!created) {
            /*
                A GET with no POST in any ancestor.
                This could happen if the API is "read-only".

                TODO: In such case, would really need to handle things like
                direct creation of data in the DB (for example)
             */
        } else {
            //only lock path params if it is not a single GET
            test.forEach { t ->
                preventPathParamMutation(t)
            }
        }

        if (created && !get.path.isLastElementAParameter()) {

            val lastPost = test[test.size - 2]
            Lazy.assert{lastPost.verb == HttpVerb.POST}

            val available = getMaxTestSizeDuringSampler() - test.size

            if (lastPost.path.isEquivalent(get.path) && available > 0) {
                /*
                 The endpoint might represent a collection, ie we
                 can be in the case:

                  POST /api/elements
                  GET  /api/elements

                 Therefore, to properly test the GET, we might
                 need to be able to create many elements.
                 */
                log.trace("Creating POSTs on collection before a GET")
                val k = 1 + randomness.nextInt(available)

                (0 until k).forEach {
                    val create = builder.createActionFor(lastPost, get)
                    preventPathParamMutation(create)
                    create.locationId = lastPost.locationId

                    //add just before the last GET
                    test.add(test.size - 1, create)
                }

                return SampleType.REST_SMART_GET_COLLECTION
            }
        }

        return SampleType.SMART
    }




    private fun preventPathParamMutation(action: RestCallAction) {
        action.parameters.forEach { p -> if (p is PathParam) p.preventMutation() }
    }





    override fun customizeAdHocInitialIndividuals() {

        adHocInitialIndividuals.clear()

        //init first sampling with 1-action call per endpoint, for all auths

        createSingleCallOnEachEndpoint(HttpWsNoAuth())

        authentications.getOfType(HttpWsAuthenticationInfo::class.java).forEach { auth ->
            createSingleCallOnEachEndpoint(auth)
        }
    }

    private fun createSingleCallOnEachEndpoint(auth: HttpWsAuthenticationInfo) {
        actionCluster.asSequence()
                .filter { a -> a.value is RestCallAction }
                .forEach { a ->
                    val copy = a.value.copy() as RestCallAction
                    copy.auth = auth
                    copy.doInitialize(randomness)
                    val ind = createIndividual(SampleType.SMART, mutableListOf(copy))
                    ind.doGlobalInitialize(searchGlobalState)
                    adHocInitialIndividuals.add(ind)
                }
    }



}
