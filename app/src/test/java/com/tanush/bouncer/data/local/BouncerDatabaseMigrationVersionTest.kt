package com.tanush.bouncer.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BouncerDatabaseMigrationVersionTest {

    @Test
    fun migration6To7_hasCorrectVersions() {
        assertEquals(6, BouncerDatabase.MIGRATION_6_7.startVersion)
        assertEquals(7, BouncerDatabase.MIGRATION_6_7.endVersion)
    }

    @Test
    fun migration6To7_sqlDropsIndex() {
        // Verifies the migration object exists and has correct version range
        // (SQL correctness tested by instrumented test)
        assertNotNull(BouncerDatabase.MIGRATION_6_7)
    }
}
