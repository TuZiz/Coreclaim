package com.coreclaim.storage;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DatabaseSchemaInitializerSourceTest {

    @Test
    void claimsCreateTableKeepsCreationTypeAndTeleportColumnTypesAligned() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/coreclaim/storage/DatabaseSchemaInitializer.java"));

        assertTrue(source.contains("creation_type %s NOT NULL DEFAULT 'UNKNOWN_LEGACY',"));
        assertTrue(source.contains("deny_all %s NOT NULL DEFAULT 0,"));
        assertTrue(source.contains("tp_x %s,"));
        assertTrue(source.contains(
            "booleanType(), booleanType(), booleanType(), booleanType(), creationTypeType(), booleanType(), doubleType()"
        ));
        assertTrue(source.contains("return database.isMySql() ? \"VARCHAR(32)\" : shortTextType();"));
        assertTrue(source.contains("ensureColumn(\"claims\", \"tp_x\", doubleType());"));
        assertTrue(source.contains("ensureColumn(\"claims\", \"deny_all\", booleanType() + \" NOT NULL DEFAULT 0\");"));
    }
}
