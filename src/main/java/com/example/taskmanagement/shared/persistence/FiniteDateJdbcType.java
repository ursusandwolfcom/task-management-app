package com.example.taskmanagement.shared.persistence;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.LocalDateJdbcType;

/** Preserves finite PostgreSQL dates that pgjdbc's LocalDate binder turns into -infinity. */
public class FiniteDateJdbcType extends LocalDateJdbcType {
    private static final LocalDate DRIVER_MIN_DATE = LocalDate.of(-4712, 1, 1);
    private static final DateTimeFormatter BC_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd G", Locale.ROOT);

    @Override
    public <T> ValueBinder<T> getBinder(JavaType<T> javaType) {
        var standard = super.getBinder(javaType);
        return new BasicBinder<>(javaType, this) {
            @Override
            protected void doBindNull(PreparedStatement statement, int index, WrapperOptions options)
                    throws SQLException {
                standard.bind(statement, null, index, options);
            }

            @Override
            protected void doBindNull(CallableStatement statement, String name, WrapperOptions options)
                    throws SQLException {
                standard.bind(statement, null, name, options);
            }

            @Override
            protected void doBind(PreparedStatement statement, T value, int index, WrapperOptions options)
                    throws SQLException {
                LocalDate date = javaType.unwrap(value, LocalDate.class, options);
                if (requiresFiniteText(statement, date)) {
                    statement.setObject(index, BC_DATE.format(date), Types.OTHER);
                } else {
                    standard.bind(statement, value, index, options);
                }
            }

            @Override
            protected void doBind(CallableStatement statement, T value, String name, WrapperOptions options)
                    throws SQLException {
                LocalDate date = javaType.unwrap(value, LocalDate.class, options);
                if (requiresFiniteText(statement, date)) {
                    statement.setObject(name, BC_DATE.format(date), Types.OTHER);
                } else {
                    standard.bind(statement, value, name, options);
                }
            }
        };
    }

    private static boolean requiresFiniteText(PreparedStatement statement, LocalDate date) throws SQLException {
        // DATE_END/JULIAN day zero allows late 4714 BC, but pgjdbc clamps before 4713 BC.
        return date.isBefore(DRIVER_MIN_DATE)
                && "PostgreSQL".equals(statement.getConnection().getMetaData().getDatabaseProductName());
    }
}
