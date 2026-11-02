package com.tanush.bouncer.ui.screens

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.res.stringResource
import com.tanush.bouncer.R
import com.tanush.bouncer.data.remote.H2HPair
import com.tanush.bouncer.ui.components.HistoricalStatsCard
import com.tanush.bouncer.ui.components.v2.HistoryCoverageNotice

internal fun LazyListScope.headToHeadItems(pair: H2HPair) {
    item(key = "head_to_head") { HistoricalStatsCard(data = pair.history) }
    if (pair.fromArchive) {
        item(key = "head_to_head_source") { HistoryCoverageNotice(stringResource(R.string.history_records_from_archive)) }
    }
}
