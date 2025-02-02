package org.evomaster.client.java.instrumentation.coverage.methodreplacement.thirdpartyclasses;

import org.evomaster.client.java.instrumentation.coverage.methodreplacement.Replacement;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.ThirdPartyMethodReplacementClass;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.UsageFilter;
import org.evomaster.client.java.instrumentation.object.ClassToSchema;
import org.evomaster.client.java.instrumentation.object.JsonTaint;
import org.evomaster.client.java.instrumentation.shared.ReplacementCategory;
import org.evomaster.client.java.instrumentation.shared.ReplacementType;


import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Objects;
import java.util.stream.Collectors;

public class JacksonObjectMapperClassReplacement extends ThirdPartyMethodReplacementClass {

    private static final JacksonObjectMapperClassReplacement singleton = new JacksonObjectMapperClassReplacement();

    @Override
    protected String getNameOfThirdPartyTargetClass() {
        /*
            this is to avoid a 2013 bug in shade plugin:
            https://issues.apache.org/jira/browse/MSHADE-156
            note that Jackson is shaded in pom of controller
         */
        return "   com.fasterxml.jackson.databind.ObjectMapper".trim();
    }

    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "Jackson_ObjectMapper_readValue_InputStream_class",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.EXT_0)
    public static <T> T readValue(Object caller, InputStream src, Class<T> valueType) throws Throwable {
        Objects.requireNonNull(caller);

        ClassToSchema.registerSchemaIfNeeded(valueType);

        /*
            To be able to check the taint, we need to read the whole stream.

            TODO: check if it has side-effects
         */

        String content = new BufferedReader(
                new InputStreamReader(src, Charset.defaultCharset()))
                .lines()
                .collect(Collectors.joining(System.lineSeparator()));

        JsonTaint.handlePossibleJsonTaint(content,valueType);

        src = new ByteArrayInputStream(content.getBytes());

        Method original = getOriginal(singleton, "Jackson_ObjectMapper_readValue_InputStream_class", caller);

        try {
            return (T) original.invoke(caller, src, valueType);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw  e.getCause();
        }
    }

    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "Jackson_ObjectMapper_readValue_Generic_class",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.EXT_0)
    public static <T> T readValue(Object caller, String content, Class<T> valueType) throws Throwable {
        Objects.requireNonNull(caller);

        ClassToSchema.registerSchemaIfNeeded(valueType);
        JsonTaint.handlePossibleJsonTaint(content,valueType);

        // JSON can be unwrapped using different approaches
        // val dto: FooDto = mapper.readValue(json)
        // To support this way, Jackson should be used inside the instrumentation
        // as shaded dependency. And that crates new problems.
        // Note: For now it's not supported

        Method original = getOriginal(singleton, "Jackson_ObjectMapper_readValue_Generic_class", caller);

        try {
            return (T) original.invoke(caller, content, valueType);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw  e.getCause();
        }
    }


    @Replacement(replacingStatic = false,
            type = ReplacementType.TRACKER,
            id = "Jackson_ObjectMapper_convertValue_Generic_class",
            usageFilter = UsageFilter.ANY,
            category = ReplacementCategory.EXT_0)
    public static <T> T convertValue(Object caller, Object fromValue, Class<T> valueType) throws Throwable {
        Objects.requireNonNull(caller);

        ClassToSchema.registerSchemaIfNeeded(valueType);

        if(fromValue instanceof String) {
            JsonTaint.handlePossibleJsonTaint((String) fromValue, valueType);
        }

        Method original = getOriginal(singleton, "Jackson_ObjectMapper_convertValue_Generic_class", caller);

        try {
            return (T) original.invoke(caller, fromValue, valueType);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw  e.getCause();
        }
    }

}
