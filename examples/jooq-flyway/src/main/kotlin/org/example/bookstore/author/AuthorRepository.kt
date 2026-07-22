package org.example.bookstore.author

import org.example.bookstore.generated.tables.records.AuthorRecord
import org.example.bookstore.generated.tables.references.AUTHOR
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import kotlin.text.set

@Repository
class AuthorRepository(private val dsl: DSLContext) {

    fun findAll(): List<AuthorDto> =
        dsl.selectFrom(AUTHOR)
            .orderBy(AUTHOR.LAST_NAME, AUTHOR.FIRST_NAME)
            .fetch()
            .map { it.toDto() }

    fun findById(id: Long): AuthorDto? =
        dsl.selectFrom(AUTHOR)
            .where(AUTHOR.ID.eq(id))
            .fetchOne()
            ?.toDto()

    fun create(req: CreateAuthorRequest): AuthorDto =
        dsl.insertInto(AUTHOR)
            .set(AUTHOR.FIRST_NAME, req.firstName)
            .set(AUTHOR.LAST_NAME, req.lastName)
            .set(AUTHOR.BIO, req.bio)
            .returning()
            .fetchOne()!!
            .toDto()

    fun update(id: Long, req: UpdateAuthorRequest): AuthorDto? {
        val record = dsl.selectFrom(AUTHOR).where(AUTHOR.ID.eq(id)).fetchOne()
            ?: return null

        req.firstName?.let { record.firstName = it }
        req.lastName?.let  { record.lastName = it }
        req.bio?.let       { record.bio = it }

        record.store()
        return record.toDto()
    }

    fun delete(id: Long): Boolean =
        dsl.deleteFrom(AUTHOR)
            .where(AUTHOR.ID.eq(id))
            .execute() > 0

    private fun AuthorRecord.toDto() = AuthorDto(
        id        = id!!,
        firstName = firstName!!,
        lastName  = lastName!!,
        bio       = bio,
        createdAt = createdAt!!,
    )
}
