package org.testcontainers.gradle.spec

class JdbcContainerSpec {
    var image: String = ""
    var databaseName: String = ""
    var username: String = ""
    var password: String = ""
    var compatibleSubstituteFor: String? = null
    var reuse: Boolean = false

    internal fun validate(name: String) {
        require(image.isNotEmpty()) { "Container '$name' error: 'image' must be explicitly specified." }
        require(databaseName.isNotEmpty()) { "Container '$name' error: 'databaseName' must be explicitly specified." }
        require(username.isNotEmpty()) { "Container '$name' error: 'username' must be explicitly specified." }
        require(password.isNotEmpty()) { "Container '$name' error: 'password' must be explicitly specified." }
    }
}
