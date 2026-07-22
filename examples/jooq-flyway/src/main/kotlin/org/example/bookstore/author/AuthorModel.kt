package org.example.bookstore.author

import java.time.OffsetDateTime

data class AuthorDto(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val bio: String?,
    val createdAt: OffsetDateTime,
)

data class CreateAuthorRequest(
    val firstName: String,
    val lastName: String,
    val bio: String? = null,
)

data class UpdateAuthorRequest(
    val firstName: String?,
    val lastName: String?,
    val bio: String?,
)
