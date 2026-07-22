package org.example.bookstore.book

import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

data class BookDto(
    val id: Long,
    val title: String,
    val isbn: String,
    val authorId: Long,
    val authorName: String,
    val published: LocalDate?,
    val price: BigDecimal?,
    val description: String?,
    val createdAt: OffsetDateTime,
)

data class CreateBookRequest(
    val title: String,
    val isbn: String,
    val authorId: Long,
    val published: LocalDate? = null,
    val price: BigDecimal? = null,
    val description: String? = null,
)

data class UpdateBookRequest(
    val title: String? = null,
    val price: BigDecimal? = null,
    val description: String? = null,
)
