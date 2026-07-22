package org.example.bookstore.author

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/authors")
class AuthorController(private val repo: AuthorRepository) {

    @GetMapping
    fun list(): List<AuthorDto> = repo.findAll()

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ResponseEntity<AuthorDto> =
        repo.findById(id)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody req: CreateAuthorRequest): AuthorDto =
        repo.create(req)

    @PatchMapping("/{id}")
    fun update(
        @PathVariable id: Long,
        @RequestBody req: UpdateAuthorRequest,
    ): ResponseEntity<AuthorDto> =
        repo.update(id, req)
            ?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.notFound().build()

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> =
        if (repo.delete(id)) ResponseEntity.noContent().build()
        else ResponseEntity.notFound().build()
}
