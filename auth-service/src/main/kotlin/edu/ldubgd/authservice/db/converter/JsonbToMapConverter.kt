package edu.ldubgd.authservice.db.converter

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.postgresql.util.PGobject
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter

@ReadingConverter
class JsonbToMapConverter : Converter<PGobject, Map<String, Any?>> {
    private val objectMapper = jacksonObjectMapper()

    override fun convert(source: PGobject): Map<String, Any?> {
        println("JsonbToMapConverter called with type: ${source::class}")
        val json = source.value
        return objectMapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})
    }
}
