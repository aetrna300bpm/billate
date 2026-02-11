package com.billate.app.data.local

import androidx.room.Embedded
import androidx.room.Relation

data class TransactionWithLineItems(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "transactionId",
    )
    val lineItems: List<LineItemEntity>,
)
