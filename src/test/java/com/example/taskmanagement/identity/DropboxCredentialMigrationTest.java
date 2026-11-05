package com.example.taskmanagement.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;

class DropboxCredentialMigrationTest {
    @Test
    void requiresRelinkWithoutChangingAnyAttachmentMetadataAndDoesNotRepeatTheReset() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:dropbox_upgrade_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            try (var setup = connection.createStatement()) {
                setup.execute("create schema task_management");
                setup.execute("create table task_management.users (id bigint primary key, version bigint not null, "
                        + "dropbox_refresh_token varchar(512), dropbox_account_id varchar(64), "
                        + "dropbox_linked_at timestamp with time zone)");
                setup.execute("create table task_management.attachments (id bigint primary key, "
                        + "uploader_id bigint references task_management.users(id), storage_key varchar(64), "
                        + "external_id varchar(128), file_name varchar(255), size_bytes bigint)");
                setup.execute("insert into task_management.users values "
                        + "(7,4,'old-offline-token','dbid:original',CURRENT_TIMESTAMP),(8,2,NULL,NULL,NULL)");
                setup.execute("insert into task_management.attachments values "
                        + "(10,7,'original-key','id:original-file','original.txt',3),"
                        + "(11,7,'local-key',NULL,'local.txt',4)");
            }
            var filesBefore = rows(connection, "select * from task_management.attachments order by id");
            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            database.setDefaultSchemaName("public");
            database.setLiquibaseSchemaName("public");
            // Match H2's DATABASE_TO_LOWER metadata when discovering an existing tracking table.
            database.setDatabaseChangeLogTableName("databasechangelog");
            database.setDatabaseChangeLogLockTableName("databasechangeloglock");
            try (var liquibase = new Liquibase("db/changelog/changes/023-protect-dropbox-credentials.yaml",
                    new ClassLoaderResourceAccessor(), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
                assertThat(rows(connection, "select id,version,dropbox_encrypted_refresh_token,dropbox_account_id,"
                        + "dropbox_linked_at from task_management.users order by id"))
                        .isEqualTo(java.util.Arrays.asList(java.util.Arrays.asList("7", "5", null, null, null),
                                java.util.Arrays.asList("8", "2", null, null, null)));
                assertThat(rows(connection, "select * from task_management.attachments order by id"))
                        .isEqualTo(filesBefore);
                assertThat(rows(connection, "select count(*) from information_schema.columns "
                        + "where table_schema='task_management' and table_name='users' "
                        + "and column_name='dropbox_refresh_token'"))
                        .containsExactly(List.of("0"));
                try (var update = connection.createStatement()) {
                    update.execute("update task_management.users set dropbox_encrypted_refresh_token='v1:fixture', "
                            + "dropbox_account_id='dbid:original',dropbox_linked_at=CURRENT_TIMESTAMP where id=7");
                }
                var relinked = rows(connection, "select * from task_management.users order by id");
                liquibase.update(new Contexts(), new LabelExpression());
                assertThat(rows(connection, "select * from task_management.users order by id")).isEqualTo(relinked);
                assertThat(rows(connection, "select * from task_management.attachments order by id"))
                        .isEqualTo(filesBefore);
                assertThat(rows(connection, "select count(*) from public.databasechangelog "
                        + "where id='023-protect-dropbox-credentials' and exectype='EXECUTED'"))
                        .containsExactly(List.of("1"));
            }
        }
    }

    private static List<List<String>> rows(Connection connection, String sql) throws Exception {
        var result = new ArrayList<List<String>>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                var values = new ArrayList<String>();
                for (int column = 1; column <= rows.getMetaData().getColumnCount(); column++) {
                    values.add(rows.getString(column));
                }
                result.add(values);
            }
        }
        return result;
    }
}
