package org.example.bookstore.book

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/books")
class BookController(private val repo: BookRepository) {

    @GetMapping
    fun list(@RequestParam authorId: Long? = null): List<BookDto> =
        if (authorId != null) repo.findByAuthor(authorId)
        else repo.findAll()

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<BookDto> =
        repo.findById(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody req: CreateBookRequest): BookDto =
        repo.create(req)

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody req: UpdateBookRequest,
    ): ResponseEntity<BookDto> =
        repo.update(id, req)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> =
        if (repo.delete(id)) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
}
