package com.billate.app.core.model

enum class Category(val displayName: String) {
    Groceries("Groceries"),
    Dining("Dining"),
    Shopping("Shopping"),
    Transport("Transport"),
    Utilities("Utilities"),
    Health("Health"),
    Entertainment("Entertainment"),
    Education("Education"),
    Other("Other");

    companion object {
        private val map = entries.associateBy { it.displayName }

        fun fromString(value: String): Category? = map[value]

        val displayNames: List<String> = entries.map { it.displayName }
    }
}
