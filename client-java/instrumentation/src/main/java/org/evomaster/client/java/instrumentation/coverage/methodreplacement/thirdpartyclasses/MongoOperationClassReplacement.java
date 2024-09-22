package org.evomaster.client.java.instrumentation.coverage.methodreplacement.thirdpartyclasses;

import org.evomaster.client.java.instrumentation.MongoFindCommand;
import org.evomaster.client.java.instrumentation.coverage.methodreplacement.ThirdPartyMethodReplacementClass;
import org.evomaster.client.java.instrumentation.object.ClassToSchema;
import org.evomaster.client.java.instrumentation.object.CustomTypeToOasConverter;
import org.evomaster.client.java.instrumentation.object.GeoJsonPointToOasConverter;
import org.evomaster.client.java.instrumentation.staticstate.ExecutionTracer;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;


public abstract class MongoOperationClassReplacement extends ThirdPartyMethodReplacementClass {

    protected static void handleMongo(Object mongoCollection, Object bson, boolean successfullyExecuted, long executionTime) {
        List<CustomTypeToOasConverter> converters = Collections.singletonList(new GeoJsonPointToOasConverter());
        String schema = ClassToSchema.getOrDeriveSchemaWithItsRef(extractDocumentsType(mongoCollection), true, converters);
        MongoFindCommand info = new MongoFindCommand(getDatabaseName(mongoCollection), getCollectionName(mongoCollection), schema, bson, successfullyExecuted, executionTime);
        ExecutionTracer.addMongoInfo(info);
    }


    private static Class<?> extractDocumentsType(Object collection) {
        try {
            Class<?> collectionClass = getCollectionClass(collection);
            return (Class<?>) collectionClass.getMethod("getDocumentClass").invoke(collection);
        } catch (NoSuchMethodException | ClassNotFoundException | InvocationTargetException |
                 IllegalAccessException e) {
            throw new RuntimeException("Failed to retrieve document's type from collection", e);
        }
    }

    private static String getDatabaseName(Object collection) {
        try {
            Class<?> collectionClass = getCollectionClass(collection);
            Object namespace = collectionClass.getMethod("getNamespace").invoke(collection);
            return (String) namespace.getClass().getMethod("getDatabaseName").invoke(namespace);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 ClassNotFoundException e) {
            throw new RuntimeException("Failed to retrieve name of the database in which collection is", e);
        }
    }

    private static String getCollectionName(Object collection) {
        try {
            Class<?> collectionClass = getCollectionClass(collection);
            Object namespace = collectionClass.getMethod("getNamespace").invoke(collection);
            return (String) namespace.getClass().getMethod("getCollectionName").invoke(namespace);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 ClassNotFoundException e) {
            throw new RuntimeException("Failed to retrieve collection name", e);
        }
    }

    private static Class<?> getCollectionClass(Object collection) throws ClassNotFoundException {
        // collection is an implementation of interface MongoCollection
        return collection.getClass().getInterfaces()[0];
    }
}
