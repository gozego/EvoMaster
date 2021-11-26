package org.evomaster.core.problem.rpc.service

import com.google.inject.AbstractModule
import com.google.inject.TypeLiteral
import org.evomaster.core.output.service.RPCTestCaseWriter
import org.evomaster.core.output.service.TestCaseWriter
import org.evomaster.core.problem.rpc.RPCIndividual
import org.evomaster.core.remote.service.RemoteController
import org.evomaster.core.search.service.Archive
import org.evomaster.core.search.service.FitnessFunction
import org.evomaster.core.search.service.Sampler
import org.evomaster.core.search.service.mutator.Mutator
import org.evomaster.core.search.service.mutator.StandardMutator
import org.evomaster.core.search.service.mutator.StructureMutator

/**
 * created by manzhang on 2021/11/26
 */
class RPCModule : AbstractModule(){

    override fun configure() {
        bind(object : TypeLiteral<Sampler<RPCIndividual>>() {})
                .to(RPCSampler::class.java)
                .asEagerSingleton()

        bind(object : TypeLiteral<Sampler<*>>() {})
                .to(RPCSampler::class.java)
                .asEagerSingleton()

        bind(RPCSampler::class.java)
                .asEagerSingleton()

        bind(object : TypeLiteral<FitnessFunction<RPCIndividual>>() {})
                .to(RPCFitness::class.java)
                .asEagerSingleton()

        bind(object : TypeLiteral<Archive<RPCIndividual>>() {})
                .asEagerSingleton()

        bind(object : TypeLiteral<Archive<*>>() {})
                .to(object : TypeLiteral<Archive<RPCIndividual>>() {})

        bind(RemoteController::class.java)
                .asEagerSingleton()

        bind(object : TypeLiteral<Mutator<RPCIndividual>>() {})
                .to(object : TypeLiteral<StandardMutator<RPCIndividual>>(){})
                .asEagerSingleton()

        bind(StructureMutator::class.java)
                .to(RPCStructureMutator::class.java)
                .asEagerSingleton()

        bind(TestCaseWriter::class.java)
                .to(RPCTestCaseWriter::class.java)
                .asEagerSingleton()

    }
}