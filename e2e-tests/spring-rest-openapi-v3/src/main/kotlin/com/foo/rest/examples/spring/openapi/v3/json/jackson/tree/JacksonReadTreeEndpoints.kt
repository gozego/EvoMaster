package com.foo.rest.examples.spring.openapi.v3.json.jackson.tree

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping(path = ["/api/jackson/tree"])
class JacksonReadTreeEndpoints {

    @GetMapping(path = [""])
    fun get() : ResponseEntity<String> {
        return ResponseEntity.ok("OK")
    }
}
