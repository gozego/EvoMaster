package org.evomaster.client.java.instrumentation.coverage.methodreplacement.thirdpartyclasses;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.evomaster.client.java.instrumentation.AdditionalInfo;
import org.evomaster.client.java.instrumentation.staticstate.ExecutionTracer;
import org.evomaster.client.java.instrumentation.staticstate.UnitsInfoRecorder;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JacksonObjectMapperTest {

    @Test
    public void testReadValue() throws Throwable {
        String json = "{\n\"count\": 10\n}";
        ObjectMapper objectMapper = new ObjectMapper();
        JacksonObjectMapperClassReplacement.readValue(objectMapper,json, JacksonTestDto.class);

        Map<String, String> parsedDto = UnitsInfoRecorder.getInstance().getParsedDtos();
        List<AdditionalInfo> additionalInfoList = ExecutionTracer.exposeAdditionalInfoList();

        Set<String> infoList = new HashSet<>();
        additionalInfoList.forEach(info -> {
            infoList.addAll(info.getParsedDtoNamesView());
        });
        assertTrue(parsedDto.containsKey(JacksonTestDto.class.getName()));
        assertTrue(infoList.contains(JacksonTestDto.class.getName()));
    }
}


class JacksonTestDto {
    public String count;
}