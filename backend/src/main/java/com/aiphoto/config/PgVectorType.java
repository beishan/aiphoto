package com.aiphoto.config;

import com.pgvector.PGvector;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.*;
import java.util.Arrays;

/**
 * Hibernate UserType for pgvector vector(512) columns.
 * Maps between float[] in Java and pgvector in PostgreSQL.
 */
public class PgVectorType implements UserType<float[]> {

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public Class<float[]> returnedClass() {
        return float[].class;
    }

    @Override
    public boolean equals(float[] a, float[] b) {
        return Arrays.equals(a, b);
    }

    @Override
    public int hashCode(float[] value) {
        return Arrays.hashCode(value);
    }

    @Override
    public float[] nullSafeGet(ResultSet rs, int position,
                                SharedSessionContractImplementor session,
                                Object owner) throws SQLException {
        Object obj = rs.getObject(position);
        if (obj == null) {
            return null;
        }
        if (obj instanceof PGvector vec) {
            return vec.toArray();
        }
        // Fallback: parse the string representation "[0.1,0.2,...]"
        String str = obj.toString();
        if (str.startsWith("[") && str.endsWith("]")) {
            str = str.substring(1, str.length() - 1);
            String[] parts = str.split(",");
            float[] result = new float[parts.length];
            for (int i = 0; i < parts.length; i++) {
                result[i] = Float.parseFloat(parts[i].trim());
            }
            return result;
        }
        return null;
    }

    @Override
    public void nullSafeSet(PreparedStatement st, float[] value,
                             int position,
                             SharedSessionContractImplementor session) throws SQLException {
        if (value == null) {
            st.setNull(position, Types.OTHER);
        } else {
            st.setObject(position, new PGvector(value));
        }
    }

    @Override
    public float[] deepCopy(float[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    @Override
    public boolean isMutable() {
        return true;
    }

    @Override
    public Serializable disassemble(float[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }

    @Override
    public float[] assemble(Serializable cached, Object owner) {
        return cached == null ? null : Arrays.copyOf((float[]) cached, ((float[]) cached).length);
    }
}
