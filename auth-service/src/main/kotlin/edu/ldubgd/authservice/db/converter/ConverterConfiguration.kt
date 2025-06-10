package edu.ldubgd.authservice.db.converter

import org.springframework.context.annotation.Configuration
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration

@Configuration
class ConverterConfiguration : AbstractJdbcConfiguration() {
    override fun userConverters(): MutableList<*> {
        println("CONVERTERS REGISTERED!!!!")
        return mutableListOf(
            MapToJsonbConverter(),
            JsonbToMapConverter()
        )
    }
}