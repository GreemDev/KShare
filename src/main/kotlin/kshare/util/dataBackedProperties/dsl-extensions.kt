package kshare.util.dataBackedProperties

import kshare.util.formatJsonString
import kshare.util.parseJsonString
import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import java.time.Instant

/**
 * A property delegate for a Kotlin object whose value is represented in the Exposed [column] as a compound JSON string value.
 * The getter parses the underlying data, and the setter sets the underlying data to its newer counterpart, as JSON.
 * @param column The varchar column whose value should be treated as a JSON string
 */
inline infix fun <ID : Comparable<ID>, reified R> Entity<ID>.serializedJson(
    column: Column<String>
): ParsedDataBackedProperty<ID, String, R> =
    serialized(column) {
        encoder { formatJsonString(it, false) }
        decoder { parseJsonString(it, false) }
    }

inline fun <reified T> Table.json(name: String, default: T? = null, collate: String? = null) =
    varchar(name, collate)
        .apply {
            if (default != null)
                default(formatJsonString(default, false))
        }

/**
 * A property delegate for a Kotlin object whose value is represented in the Exposed [column] as a [S] value;
 * specifying functions for parsing the data and serializing it back into a value of [S].
 * The most common usage of this is when S is [String] for parsing; allowing usage such as compound JSON objects as column values.
 * The getter parses the underlying data, and the setter sets the underlying data to its newer counterpart, all data passed through the specified functions.
 * @param column The data column whose value should be treated as parsable data.
 * @exception IllegalStateException thrown when the property builder does not have one or both of the value converters.
 */
fun <ID : Comparable<ID>, S, R> serialized(
    column: Column<S>,
    initializer: DataPropertyBuilder<ID, S, R>.() -> Unit
) = DataPropertyBuilder.createNew<ID, S, R>(column).apply(initializer).build()

/**
 * A property delegate for a Java [Instant] whose value is represented in the Exposed [column] as a [Long] value.
 */
fun <ID : Comparable<ID>> serializedInstant(
    column: Column<Long>
) = serialized<ID, Long, Instant>(column) {
    encoder { it.toEpochMilli() }
    decoder { Instant.ofEpochMilli(it) }
}


const val h2StringMaxLength = 1048576

//use this for declaring a JSON string column
fun Table.varchar(name: String, collate: String? = null) = varchar(name, h2StringMaxLength, collate)