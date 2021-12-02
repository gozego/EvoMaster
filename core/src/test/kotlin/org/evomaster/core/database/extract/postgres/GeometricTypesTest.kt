package org.evomaster.core.database.extract.postgres

import org.evomaster.client.java.controller.api.dto.database.schema.DatabaseType
import org.evomaster.client.java.controller.db.SqlScriptRunner
import org.evomaster.client.java.controller.internal.db.SchemaExtractor
import org.evomaster.core.database.DbActionTransformer
import org.evomaster.core.database.SqlInsertBuilder
import org.evomaster.core.search.gene.sql.geometric.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.postgresql.geometric.*

/**
 * Created by jgaleotti on 07-May-19.
 */
class GeometricTypesTest : ExtractTestBasePostgres() {

    override fun getSchemaLocation() = "/sql_schema/postgres_geometric_types.sql"


    @Test
    fun testGeometricTypes() {

        val schema = SchemaExtractor.extract(connection)

        assertNotNull(schema)

        assertEquals("public", schema.name.lowercase())
        assertEquals(DatabaseType.POSTGRES, schema.databaseType)

        val builder = SqlInsertBuilder(schema)
        val actions = builder.createSqlInsertionAction(
            "GeometricTypes", setOf(
                "pointColumn"
            )
        )

        val genes = actions[0].seeGenes()

        assertEquals(7, genes.size)
        assertTrue(genes[0] is SqlPointGene)
        assertTrue(genes[1] is SqlLineGene)
        assertTrue(genes[2] is SqlLineSegmentGene)
        assertTrue(genes[3] is SqlBoxGene)
        assertTrue(genes[4] is SqlPathGene)
        assertTrue(genes[5] is SqlPolygonGene)
        assertTrue(genes[6] is SqlCircleGene)

        val lineGene = genes[1] as SqlLineGene
        val p = lineGene.innerGene()[0] as SqlPointGene
        val q = lineGene.innerGene()[1] as SqlPointGene
        p.x.value = 0.0f
        p.y.value = 0.0f
        q.x.value = 1.0f
        q.y.value = 1.0f

        val dbCommandDto = DbActionTransformer.transform(actions)
        SqlScriptRunner.execInsert(connection, dbCommandDto.insertions)

        val resultSet = SqlScriptRunner.execCommand(connection, "SELECT * FROM GeometricTypes;")

        assertTrue(resultSet.seeRows().isNotEmpty())

        val dataRow = resultSet.seeRows().first()
        val point = dataRow.getValueByName("pointColumn") as PGpoint
        assertEquals(0.0, point.x)
        assertEquals(0.0, point.y)

        assertTrue(dataRow.getValueByName("lineColumn") is PGline)
        assertTrue(dataRow.getValueByName("lineSegmentColumn") is PGlseg)
        assertTrue(dataRow.getValueByName("boxColumn") is PGbox)
        assertTrue(dataRow.getValueByName("pathColumn") is PGpath)
        assertTrue(dataRow.getValueByName("polygonColumn") is PGpolygon)
        assertTrue(dataRow.getValueByName("circleColumn") is PGcircle)

    }
}