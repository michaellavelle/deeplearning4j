package org.datavec.spark.transform;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.sql.*;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.datavec.api.berkeley.Pair;
import org.datavec.api.transform.schema.Schema;
import org.datavec.api.writable.*;
import org.datavec.spark.transform.sparkfunction.SequenceToRows;
import org.datavec.spark.transform.sparkfunction.ToRecord;
import org.datavec.spark.transform.sparkfunction.ToRow;

import java.util.ArrayList;
import java.util.List;

import org.apache.spark.sql.functions;
import org.datavec.spark.transform.sparkfunction.sequence.DataFrameToSequenceCreateCombiner;
import org.datavec.spark.transform.sparkfunction.sequence.DataFrameToSequenceMergeCombiner;
import org.datavec.spark.transform.sparkfunction.sequence.DataFrameToSequenceMergeValue;

import static org.apache.spark.sql.functions.avg;


/**
 * Namespace for datavec
 * dataframe interop
 *
 * @author Adam Gibson
 */
public class DataFrames {

    public static final String SEQUENCE_UUID_COLUMN = "__SEQ_UUID";
    public static final String SEQUENCE_INDEX_COLUMN = "__SEQ_IDX";

    private DataFrames() {
    }

    /**
     * Standard deviation for a column
     *
     * @param dataFrame  the dataframe to
     *                   get the column from
     * @param columnName the name of the column to get the standard
     *                   deviation for
     * @return the column that represents the standard deviation
     */
    public static Column std(DataFrame dataFrame, String columnName) {
        return functions.sqrt(var(dataFrame, columnName));
    }


    /**
     * Standard deviation for a column
     *
     * @param dataFrame  the dataframe to
     *                   get the column from
     * @param columnName the name of the column to get the standard
     *                   deviation for
     * @return the column that represents the standard deviation
     */
    public static Column var(DataFrame dataFrame, String columnName) {
        return dataFrame.groupBy(columnName).agg(functions.variance(columnName)).col(columnName);
    }

    /**
     * MIn for a column
     *
     * @param dataFrame  the dataframe to
     *                   get the column from
     * @param columnName the name of the column to get the min for
     * @return the column that represents the min
     */
    public static Column min(DataFrame dataFrame, String columnName) {
        return dataFrame.groupBy(columnName).agg(functions.min(columnName)).col(columnName);
    }

    /**
     * Max for a column
     *
     * @param dataFrame  the dataframe to
     *                   get the column from
     * @param columnName the name of the column
     *                   to get the max for
     * @return the column that represents the max
     */
    public static Column max(DataFrame dataFrame, String columnName) {
        return dataFrame.groupBy(columnName).agg(functions.max(columnName)).col(columnName);
    }

    /**
     * Mean for a column
     *
     * @param dataFrame  the dataframe to
     *                   get the column fron
     * @param columnName the name of the column to get the mean for
     * @return the column that represents the mean
     */
    public static Column mean(DataFrame dataFrame, String columnName) {
        return dataFrame.groupBy(columnName).agg(avg(columnName)).col(columnName);
    }

    /**
     * Convert a datavec schema to a
     * struct type in spark
     *
     * @param schema the schema to convert
     * @return the datavec struct type
     */
    public static StructType fromSchema(Schema schema) {
        StructField[] structFields = new StructField[schema.numColumns()];
        for (int i = 0; i < structFields.length; i++) {
            switch (schema.getColumnTypes().get(i)) {
                case Double:
                    structFields[i] = new StructField(schema.getName(i), DataTypes.DoubleType, false, Metadata.empty());
                    break;
                case Integer:
                    structFields[i] = new StructField(schema.getName(i), DataTypes.IntegerType, false, Metadata.empty());
                    break;
                case Long:
                    structFields[i] = new StructField(schema.getName(i), DataTypes.LongType, false, Metadata.empty());
                    break;
                case Float:
                    structFields[i] = new StructField(schema.getName(i), DataTypes.FloatType, false, Metadata.empty());
                    break;
                default:
                    throw new IllegalStateException("This api should not be used with strings , binary data or ndarrays. This is only for columnar data");
            }
        }
        return new StructType(structFields);
    }

    /**
     * Convert the DataVec sequence schema to a StructType for Spark, for example for use in
     * {@link #toDataFrameSequence(Schema, JavaRDD)}}
     * <b>Note</b>: as per {@link #toDataFrameSequence(Schema, JavaRDD)}}, the StructType has two additional columns added to it:<br>
     * - Column 0: Sequence UUID (name: {@link #SEQUENCE_UUID_COLUMN}) - a UUID for the original sequence<br>
     * - Column 1: Sequence index (name: {@link #SEQUENCE_INDEX_COLUMN} - an index (integer, starting at 0) for the position
     * of this record in the original time series.<br>
     * These two columns are required if the data is to be converted back into a sequence at a later point, for example
     * using {@link #toRecordsSequence(DataFrame)}
     *
     * @param schema Schema to convert
     * @return StructType for the schema
     */
    public static StructType fromSchemaSequence(Schema schema) {
        StructField[] structFields = new StructField[schema.numColumns() + 2];

        structFields[0] = new StructField(SEQUENCE_UUID_COLUMN, DataTypes.StringType, false, Metadata.empty());
        structFields[1] = new StructField(SEQUENCE_INDEX_COLUMN, DataTypes.IntegerType, false, Metadata.empty());

        for (int i = 0; i < schema.numColumns(); i++) {
            switch (schema.getColumnTypes().get(i)) {
                case Double:
                    structFields[i + 2] = new StructField(schema.getName(i), DataTypes.DoubleType, false, Metadata.empty());
                    break;
                case Integer:
                    structFields[i + 2] = new StructField(schema.getName(i), DataTypes.IntegerType, false, Metadata.empty());
                    break;
                case Long:
                    structFields[i + 2] = new StructField(schema.getName(i), DataTypes.LongType, false, Metadata.empty());
                    break;
                case Float:
                    structFields[i + 2] = new StructField(schema.getName(i), DataTypes.FloatType, false, Metadata.empty());
                    break;
                default:
                    throw new IllegalStateException("This api should not be used with strings , binary data or ndarrays. This is only for columnar data");
            }
        }
        return new StructType(structFields);
    }


    /**
     * Create a datavec schema
     * from a struct type
     *
     * @param structType the struct type to create the schema from
     * @return the created schema
     */
    public static Schema fromStructType(StructType structType) {
        Schema.Builder builder = new Schema.Builder();
        StructField[] fields = structType.fields();
        String[] fieldNames = structType.fieldNames();
        for (int i = 0; i < fields.length; i++) {
            String name = fields[i].dataType().typeName().toLowerCase();
            switch (name) {
                case "double":
                    builder.addColumnDouble(fieldNames[i]);
                    break;
                case "float":
                    builder.addColumnFloat(fieldNames[i]);
                    break;
                case "long":
                    builder.addColumnLong(fieldNames[i]);
                    break;
                case "int":
                case "integer":
                    builder.addColumnInteger(fieldNames[i]);
                    break;
                case "string":
                    builder.addColumnString(fieldNames[i]);
                    break;
                default:
                    throw new RuntimeException("Unknown type: " + name);
            }
        }

        return builder.build();
    }


    /**
     * Create a compatible schema
     * and rdd for datavec
     *
     * @param dataFrame the dataframe to convert
     * @return the converted schema and rdd of writables
     */
    public static Pair<Schema, JavaRDD<List<Writable>>> toRecords(DataFrame dataFrame) {
        Schema schema = fromStructType(dataFrame.schema());
        return new Pair<>(schema, dataFrame.javaRDD().map(new ToRecord(schema)));
    }

    /**
     * Convert the given DataFrame to a sequence<br>
     * <b>Note</b>: It is assumed here that the DataFrame has been created by {@link #toDataFrameSequence(Schema, JavaRDD)}.
     * In particular:<br>
     * - the first column is a UUID for the original sequence the row is from<br>
     * - the second column is a time step index: where the row appeared in the original sequence<br>
     * <p>
     * Typical use: Normalization via the {@link Normalization} static methods
     *
     * @param dataFrame Data frame to convert
     * @return Data in sequence (i.e., {@code List<List<Writable>>} form
     */
    public static Pair<Schema, JavaRDD<List<List<Writable>>>> toRecordsSequence(DataFrame dataFrame) {

        //Need to convert from flattened to sequence data...
        //First: Group by the Sequence UUID (first column)
        JavaPairRDD<String, Iterable<Row>> grouped = dataFrame.javaRDD().groupBy(new Function<Row, String>() {
            @Override
            public String call(Row row) throws Exception {
                return row.getString(0);
            }
        });


        Schema schema = fromStructType(dataFrame.schema());

        //Group by sequence UUID, and sort each row within the sequences using the time step index
        Function<Iterable<Row>, List<List<Writable>>> createCombiner = new DataFrameToSequenceCreateCombiner(schema);   //Function to create the initial combiner
        Function2<List<List<Writable>>, Iterable<Row>, List<List<Writable>>> mergeValue = new DataFrameToSequenceMergeValue(schema);    //Function to add a row
        Function2<List<List<Writable>>, List<List<Writable>>, List<List<Writable>>> mergeCombiners = new DataFrameToSequenceMergeCombiner();    //Function to merge existing sequence writables

        JavaRDD<List<List<Writable>>> sequences = grouped.combineByKey(createCombiner, mergeValue, mergeCombiners).values();

        //We no longer want/need the sequence UUID and sequence time step columns - extract those out
        JavaRDD<List<List<Writable>>> out = sequences.map(new Function<List<List<Writable>>, List<List<Writable>>>() {
            @Override
            public List<List<Writable>> call(List<List<Writable>> v1) throws Exception {
                List<List<Writable>> out = new ArrayList<>(v1.size());
                for (List<Writable> l : v1) {
                    List<Writable> subset = new ArrayList<>();
                    for (int i = 2; i < l.size(); i++) {
                        subset.add(l.get(i));
                    }
                    out.add(subset);
                }
                return out;
            }
        });

        return new Pair<>(schema, out);
    }

    /**
     * Creates a data frame from a collection of writables
     * rdd given a schema
     *
     * @param schema the schema to use
     * @param data   the data to convert
     * @return the dataframe object
     */
    public static DataFrame toDataFrame(Schema schema, JavaRDD<List<Writable>> data) {
        JavaSparkContext sc = new JavaSparkContext(data.context());
        SQLContext sqlContext = new SQLContext(sc);
        JavaRDD<Row> rows = data.map(new ToRow(schema));
        DataFrame dataFrame = sqlContext.createDataFrame(rows, fromSchema(schema));
        return dataFrame;
    }


    /**
     * Convert the given sequence data set to a DataFrame.<br>
     * <b>Note</b>: The resulting DataFrame has two additional columns added to it:<br>
     * - Column 0: Sequence UUID (name: {@link #SEQUENCE_UUID_COLUMN}) - a UUID for the original sequence<br>
     * - Column 1: Sequence index (name: {@link #SEQUENCE_INDEX_COLUMN} - an index (integer, starting at 0) for the position
     * of this record in the original time series.<br>
     * These two columns are required if the data is to be converted back into a sequence at a later point, for example
     * using {@link #toRecordsSequence(DataFrame)}
     *
     * @param schema Schema for the data
     * @param data   Sequence data to convert to a DataFrame
     * @return The dataframe object
     */
    public static DataFrame toDataFrameSequence(Schema schema, JavaRDD<List<List<Writable>>> data) {
        JavaSparkContext sc = new JavaSparkContext(data.context());

        SQLContext sqlContext = new SQLContext(sc);
        JavaRDD<Row> rows = data.flatMap(new SequenceToRows(schema));
        DataFrame dataFrame = sqlContext.createDataFrame(rows, fromSchemaSequence(schema));
        return dataFrame;
    }

    /**
     * Convert a given Row to a list of writables, given the specified Schema
     *
     * @param schema Schema for the data
     * @param row    Row of data
     */
    public static List<Writable> rowToWritables(Schema schema, Row row) {
        List<Writable> ret = new ArrayList<>();
        for (int i = 0; i < row.size(); i++) {
            switch (schema.getType(i)) {
                case Double:
                    ret.add(new DoubleWritable(row.getDouble(i)));
                    break;
                case Float:
                    ret.add(new FloatWritable(row.getFloat(i)));
                    break;
                case Integer:
                    ret.add(new IntWritable(row.getInt(i)));
                    break;
                case Long:
                    ret.add(new LongWritable(row.getLong(i)));
                    break;
                case String:
                    ret.add(new Text(row.getString(i)));
                    break;
                default:
                    throw new IllegalStateException("Illegal type");
            }
        }
        return ret;
    }
}
