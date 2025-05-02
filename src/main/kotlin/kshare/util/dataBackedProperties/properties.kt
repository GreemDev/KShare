package kshare.util.dataBackedProperties

import org.jetbrains.exposed.dao.Entity
import org.jetbrains.exposed.sql.Column
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

abstract class DataPropertyBuilder<ID : Comparable<ID>, S, R>(private val column: Column<S>) {
    companion object {
        fun<ID : Comparable<ID>, S, R> createNew(column: Column<S>) =
            object : DataPropertyBuilder<ID, S, R>(column) {}
    }

    private var encoder: Parser<R, S>? = null
    private var decoder: Parser<S, R>? = null

    infix fun encoder(encoder: Parser<R, S>): DataPropertyBuilder<ID, S, R> {
        this.encoder = encoder
        return this
    }

    infix fun decoder(decoder: Parser<S, R>): DataPropertyBuilder<ID, S, R> {
        this.decoder = decoder
        return this
    }

    infix fun encoder(block: (R) -> S): DataPropertyBuilder<ID, S, R> = encoder(newCustomParser(block))
    infix fun decoder(block: (S) -> R): DataPropertyBuilder<ID, S, R> = decoder(newCustomParser(block))

    fun build(): ParsedDataBackedProperty<ID, S, R> {
        val errorMessage by lazy {
            "${ParsedDataBackedProperty::class.simpleName} cannot be created without both encoding and decoding functions."
        }
        checkNotNull(encoder) { errorMessage }
        checkNotNull(decoder) { errorMessage }
        return ParsedDataBackedProperty(column, decoder!!, encoder!!)
    }
}

open class ParsedDataBackedProperty<ID : Comparable<ID>, S, R>(
    private val column: Column<S>,
    private val fromData: Parser<S, R>,
    private val toData: Parser<R, S>
) : ReadWriteProperty<Entity<ID>, R> {
    override fun getValue(thisRef: Entity<ID>, property: KProperty<*>): R = thisRef.run {
        val raw = column.getValue(this, property)
        return fromData.unsafeParse(raw)
    }

    override fun setValue(thisRef: Entity<ID>, property: KProperty<*>, value: R) {
        thisRef.apply {
            val string = toData.unsafeParse(value)
            column.setValue(this, property, string)
        }
    }
}