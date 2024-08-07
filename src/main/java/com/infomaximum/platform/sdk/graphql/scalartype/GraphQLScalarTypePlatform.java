package com.infomaximum.platform.sdk.graphql.scalartype;

import com.infomaximum.cluster.graphql.schema.scalartype.GraphQLScalarTypeCustom;
import com.infomaximum.cluster.graphql.schema.scalartype.GraphQLTypeScalar;
import com.infomaximum.platform.sdk.graphql.out.GOutputFile;
import graphql.language.IntValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.time.Duration;

/**
 * Created by kris on 19.01.17.
 */
public class GraphQLScalarTypePlatform {


    public static final GraphQLTypeScalar GraphQLDuration = new GraphQLTypeScalar(
            "Duration", "Built-in Duration",
            Duration.class,
            new Coercing() {

                @Override
                public Object serialize(Object input) {
                    if (input==null) return null;
                    if (input instanceof Duration) {
                        return ((Duration) input).toMillis();
                    } else if (input instanceof Number) {
                        return ((Number)input).longValue();
                    } else {
                        throw new CoercingSerializeException(
                                "Expected type 'Duration' but was '" + typeName(input) + "'."
                        );
                    }
                }

                @Override
                public Object parseValue(Object input) {
                    if (input instanceof Duration) {
                        return input;
                    } else if (GraphQLScalarTypeCustom.isConvertToNumber(input)) {
                        long value = GraphQLScalarTypeCustom.toNumber(input).longValue();
                        if (value < 0) throw new CoercingParseValueException("Invalid value: " + value);
                        return Duration.ofMillis(value);
                    } else {
                        throw new CoercingParseValueException(
                                "Expected type 'Duration' but was '" + typeName(input) + "'."
                        );
                    }
                }

                @Override
                public Object parseLiteral(Object input) {
                    if (input instanceof IntValue) {
                        long value = ((IntValue) input).getValue().longValue();
                        if (value < 0) throw new CoercingParseValueException("Invalid value: " + value);
                        return Duration.ofMillis(value);
                    }
                    throw new CoercingParseLiteralException(
                            "Expected AST type 'IntValue' but was '" + typeName(input) + "'."
                    );
                }
            }
    );

    public static final GraphQLTypeScalar GraphQLGOutputFile = new GraphQLTypeScalar(
            "output_file", "OutputFile",
            GOutputFile.class,
            new Coercing() {

                @Override
                public Object serialize(Object input) {
                    return input;
                }

                @Override
                public Object parseValue(Object input) {
                    throw new RuntimeException("Not support.");
                }

                @Override
                public Object parseLiteral(Object input) {
                    throw new RuntimeException("Not support.");
                }
            }
    );

    private static String typeName(Object input) {
        if (input == null) {
            return "null";
        }
        return input.getClass().getSimpleName();
    }
}