package org.example.bookstore.book

import org.example.bookstore.generated.tables.references.AUTHOR
import org.example.bookstore.generated.tables.references.BOOK
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository

@Repository
class BookRepository(private val dsl: DSLContext) {

    private fun Record.toDto() = BookDto(
        id = get(BOOK.ID)!!,
        title = get(BOOK.TITLE)!!,
        isbn = get(BOOK.ISBN)!!,
        authorId = get(BOOK.AUTHOR_ID)!!,
        authorName = "${get(AUTHOR.FIRST_NAME)} ${get(AUTHOR.LAST_NAME)}",
        published = get(BOOK.PUBLISHED),
        price = get(BOOK.PRICE),
        description = get(BOOK.DESCRIPTION),
        createdAt = get(BOOK.CREATED_AT)!!,
    )

    private fun baseQuery() =
        dsl.select(
            BOOK.ID, BOOK.TITLE, BOOK.ISBN, BOOK.AUTHOR_ID,
            BOOK.PUBLISHED, BOOK.PRICE, BOOK.DESCRIPTION, BOOK.CREATED_AT,
            AUTHOR.FIRST_NAME, AUTHOR.LAST_NAME,
        )
            .from(BOOK)
            .join(AUTHOR).on(BOOK.AUTHOR_ID.eq(AUTHOR.ID))

    fun findAll(): List<BookDto> =
        baseQuery()
            .orderBy(BOOK.TITLE)
            .fetch()
            .map { it.toDto() }

    fun findById(id: Long): BookDto? =
        baseQuery()
            .where(BOOK.ID.eq(id))
            .fetchOne()
            ?.toDto()

    fun findByAuthor(authorId: Long): List<BookDto> =
        baseQuery()
            .where(BOOK.AUTHOR_ID.eq(authorId))
            .orderBy(BOOK.PUBLISHED.desc())
            .fetch()
            .map { it.toDto() }

    fun create(req: CreateBookRequest): BookDto {
        val record = dsl.insertInto(BOOK)
            .set(BOOK.TITLE, req.title)
            .set(BOOK.ISBN, req.isbn)
            .set(BOOK.AUTHOR_ID, req.authorId)
            .set(BOOK.PUBLISHED, req.published)
            .set(BOOK.PRICE, req.price)
            .set(BOOK.DESCRIPTION, req.description)
            .returning(BOOK.ID)
            .fetchOne()!!

        return findById(record.getValue(BOOK.ID)!!)!!
    }

    fun update(id: Long, req: UpdateBookRequest): BookDto? {
        val existing = dsl.selectFrom(BOOK).where(BOOK.ID.eq(id)).fetchOne()
            ?: return null

        req.title?.let { existing.title = it }
        req.price?.let { existing.price = it }
        req.description?.let { existing.description = it }

        existing.store()
        return findById(id)
    }

    fun delete(id: Long): Boolean =
        dsl.deleteFrom(BOOK)
            .where(BOOK.ID.eq(id))
            .execute() > 0
}
