package edu.ldubgd.authservice.db.converter

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.postgresql.util.PGobject
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.WritingConverter

@WritingConverter
class MapToJsonbConverter(
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) : Converter<Map<String, Any?>, PGobject> {

    override fun convert(source: Map<String, Any?>): PGobject {
        val json = objectMapper.writeValueAsString(source)
        println("MapToJsonbConverter: $json")
        return PGobject().apply {
            type = "jsonb"
            value = json
        }
    }

}
