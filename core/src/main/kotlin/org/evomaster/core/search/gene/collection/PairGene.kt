package org.evomaster.core.search.gene.collection

import org.evomaster.core.output.OutputFormat
import org.evomaster.core.search.gene.Gene
import org.evomaster.core.search.gene.string.StringGene
import org.evomaster.core.search.gene.root.CompositeFixedGene
import org.evomaster.core.search.gene.utils.GeneUtils
import org.evomaster.core.search.service.Randomness
import org.evomaster.core.search.service.mutator.genemutation.AdditionalGeneMutationInfo
import org.evomaster.core.search.service.mutator.genemutation.SubsetGeneMutationSelectionStrategy

/**
 * handling pair type or Map.Entry type
 */
class PairGene<F,S>(
        name: String,
        val first: F,
        val second : S,
        /**
         *
         * whether the [first] is mutable
         */
        val isFirstMutable : Boolean = true //TODO shouldn't be first.isMutable()?
): CompositeFixedGene(name, listOf(first, second)) where F: Gene, S: Gene {

    companion object{

        /**
         * create simple pair gene based on [gene]
         * first is a StringGene and its name is based on the name of [gene]
         * second is [gene]
         * @param gene is the second of the pair
         * @param isFixedFirst specifies whether the first is fixed value.
         */
        fun <T: Gene> createStringPairGene(gene: T, isFixedFirst: Boolean = false) : PairGene<StringGene, T> {
            val key = StringGene(gene.name)
            if (isFixedFirst)
                key.value = gene.name
            return PairGene(gene.name, key, gene, isFirstMutable = !isFixedFirst)
        }

    }

    override fun isLocallyValid() : Boolean{
        return getViewOfChildren().all { it.isLocallyValid() }
    }

    override fun randomize(randomness: Randomness, tryToForceNewValue: Boolean) {
        if(first.isMutable()) {
            first.randomize(randomness, tryToForceNewValue)
        }
        if(second.isMutable()) {
            second.randomize(randomness, tryToForceNewValue)
        }
    }

    override fun getValueAsPrintableString(previousGenes: List<Gene>, mode: GeneUtils.EscapeMode?, targetFormat: OutputFormat?, extraCheck: Boolean): String {
        return "${first.getValueAsPrintableString(targetFormat = targetFormat)}:${second.getValueAsPrintableString(targetFormat = targetFormat)}"
    }



    override fun copyValueFrom(other: Gene) {
        if (other !is PairGene<*, *>) {
            throw IllegalArgumentException("Invalid gene type ${other.javaClass}")
        }
        first.copyValueFrom(other.first)
        second.copyValueFrom(other.second)
    }

    override fun containsSameValueAs(other: Gene): Boolean {
        if (other !is PairGene<*, *>) {
            throw IllegalArgumentException("Invalid gene type ${other.javaClass}")
        }
        return first.containsSameValueAs(other.first) && second.containsSameValueAs(other.second)
    }


    override fun bindValueBasedOn(gene: Gene): Boolean {
        if (gene !is PairGene<*, *>) {
            throw IllegalArgumentException("Invalid gene type ${gene.javaClass}")
        }
        return first.bindValueBasedOn(gene.first) && second.bindValueBasedOn(gene.second)
    }


    override fun copyContent(): Gene {
        return PairGene(name, first.copy(), second.copy(), isFirstMutable)
    }

    override fun isMutable(): Boolean {
        /*
            Can be tricky... assume a first that is mutable, but we do not want to change it.
            we still need to initialize with randomize, otherwise its constraints might fail
         */
        return (first.isMutable() && (isFirstMutable || !first.initialized)) || second.isMutable()
    }

    override fun isPrintable(): Boolean {
        return first.isPrintable() && second.isPrintable()
    }

    override fun mutablePhenotypeChildren(): List<Gene> {

        /*
            FIXME what is the role of isFirstMutable???
            couldn't had used a DisruptiveGene instead?
         */

        val list = mutableListOf<Gene>()
        if (first.isMutable() && isFirstMutable)
            list.add(first)
        if (second.isMutable())
            list.add(second)
        return list
    }

    override fun mutationWeight(): Double {
        return (if (isFirstMutable) first.mutationWeight() else 0.0) + second.mutationWeight()
    }

    override fun customShouldApplyShallowMutation(
        randomness: Randomness,
        selectionStrategy: SubsetGeneMutationSelectionStrategy,
        enableAdaptiveGeneMutation: Boolean,
        additionalGeneMutationInfo: AdditionalGeneMutationInfo?
    ): Boolean {
        return false
    }

}