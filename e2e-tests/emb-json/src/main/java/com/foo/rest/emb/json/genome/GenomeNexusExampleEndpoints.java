package com.foo.rest.emb.json.genome;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.ws.rs.core.MediaType;
import java.util.Map;

@RestController
@RequestMapping(path = "/api")
public class GenomeNexusExampleEndpoints {

    @RequestMapping(
            value = "/json",
            method = RequestMethod.POST,
            produces = MediaType.APPLICATION_JSON
    )
    public ResponseEntity parseJson(@RequestBody String json) {
        TokenMapConverter converter = new TokenMapConverter();

        Map<String, String> objects = converter.convertToMap(json);

        if (objects.containsKey("teal")) {
            return ResponseEntity.status(200).body("Teal");
        }

        return ResponseEntity.status(204).body("Working");
    }
}
