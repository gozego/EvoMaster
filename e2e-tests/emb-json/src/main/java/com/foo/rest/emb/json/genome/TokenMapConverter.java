package com.foo.rest.emb.json.genome;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.HashMap;
import java.util.Map;

public class TokenMapConverter {

    public Map<String, String> convertToMap(String token) {
        Gson gson = new Gson();
        Map<String, String> tokenMap = new HashMap<String, String>();
        try {
            tokenMap = gson.fromJson(token, Map.class);
        }
        catch (JsonParseException e) {
            System.out.println("The format of token is invalid. For example {\"source1\":\"put-your-token1-here\",\"source2\":\"put-your-token2-here\"}");
        }
        return tokenMap;
    }
}
