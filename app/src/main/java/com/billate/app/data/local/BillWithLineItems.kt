package com.billate.app.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class BillWithLineItems(
    @Embedded val bill: BillTransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "billId",
    )
    val lineItems: List<LineItemEntity>,
)
