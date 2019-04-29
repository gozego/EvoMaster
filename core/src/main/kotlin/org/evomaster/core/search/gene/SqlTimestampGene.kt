package org.evomaster.core.search.gene

import org.evomaster.core.output.OutputFormat


class SqlTimestampGene(
        name: String,
        date: DateGene = DateGene("date"),
        time: TimeGene = TimeGene("time", withMsZ = false)
) : DateTimeGene(name, date, time) {

    init {
        require(!time.withMsZ) { "Must not have MsZ in SQL Timestamps" }
    }

    override fun copy(): Gene = SqlTimestampGene(
            name,
            date.copy() as DateGene,
            time.copy() as TimeGene
    )

    override fun getValueAsPrintableString(previousGenes: List<Gene>, mode: String?, targetFormat: OutputFormat?): String {
        return "\"${getValueAsRawString()}\""
    }

    override fun getValueAsRawString(): String {
        return "${date.getValueAsRawString()}" +
                " " +
                "${time.getValueAsRawString()}"
    }

    override fun copyValueFrom(other: Gene) {
        if (other !is SqlTimestampGene) {
            throw IllegalArgumentException("Invalid gene type ${other.javaClass}")
        }
        this.date.copyValueFrom(other.date)
        this.time.copyValueFrom(other.time)
    }

    override fun containsSameValueAs(other: Gene): Boolean {
        if (other !is SqlTimestampGene) {
            throw IllegalArgumentException("Invalid gene type ${other.javaClass}")
        }
        return this.date.containsSameValueAs(other.date)
                && this.time.containsSameValueAs(other.time)
    }

    override fun flatView(excludePredicate: (Gene) -> Boolean): List<Gene>{
        return if(excludePredicate(this)) listOf() else
            listOf(this).plus(date.flatView(excludePredicate))
                    .plus(time.flatView(excludePredicate))
    }
}