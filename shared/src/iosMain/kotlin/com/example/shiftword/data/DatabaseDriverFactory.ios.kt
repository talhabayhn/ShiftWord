package com.example.shiftword.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.example.shiftword.db.WordShiftDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(WordShiftDatabase.Schema, "wordshift.db")
}
