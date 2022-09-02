package org.evomaster.core.search.gene

import org.evomaster.core.search.StructuralElement
import org.evomaster.core.search.service.AdaptiveParameterControl
import org.evomaster.core.search.service.Randomness
import org.evomaster.core.search.service.mutator.MutationWeightControl
import org.evomaster.core.search.service.mutator.genemutation.AdditionalGeneMutationInfo
import org.evomaster.core.search.service.mutator.genemutation.SubsetGeneSelectionStrategy

/**
 * A basic gene that contains no internal genes
 */
abstract class SimpleGene(name: String) : Gene(name, mutableListOf()){

    private val errorChildMsg = "BUG in EvoMaster: cannot modify children of childless ${this.javaClass}"

    override fun addChild(child: StructuralElement) {
        throw IllegalStateException(errorChildMsg)
    }

    override fun addChild(position: Int, child: StructuralElement){
        throw IllegalStateException(errorChildMsg)
    }

    override fun addChildren(position: Int, list : List<StructuralElement>){
        throw IllegalStateException(errorChildMsg)
    }

    override fun killAllChildren(){
        throw IllegalStateException(errorChildMsg)
    }

    override fun killChild(child: StructuralElement){
        throw IllegalStateException(errorChildMsg)
    }

    override fun killChildByIndex(index: Int) : StructuralElement{
        throw IllegalStateException(errorChildMsg)
    }

    override fun killChildren(predicate: (StructuralElement) -> Boolean){
        throw IllegalStateException(errorChildMsg)
    }

    override fun killChildren(toKill: List<out StructuralElement>){
        throw IllegalStateException(errorChildMsg)
    }


    //TODO should it be final? some simple genes seems to use it...
    override fun copyContent(): Gene {
        throw IllegalStateException("Bug in ${this::class.java.simpleName}: copyContent() must not be called on a SimpleGene")
    }

    final override fun candidatesInternalGenes(randomness: Randomness,
                                apc: AdaptiveParameterControl,
                                selectionStrategy: SubsetGeneSelectionStrategy,
                                enableAdaptiveGeneMutation: Boolean,
                                additionalGeneMutationInfo: AdditionalGeneMutationInfo?
    ): List<Gene>{
        return listOf()
    }



    final override fun adaptiveSelectSubset(randomness: Randomness,
                                            internalGenes: List<Gene>,
                                            mwc: MutationWeightControl,
                                            additionalGeneMutationInfo: AdditionalGeneMutationInfo
    ): List<Pair<Gene, AdditionalGeneMutationInfo?>> {
        throw IllegalStateException("adaptive gene selection is unavailable for the gene ${this::class.java.simpleName}")
    }

    final override fun innerGene(): List<Gene> = listOf()
}