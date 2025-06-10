package edu.ldubgd.authservice.db.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import edu.ldubgd.authservice.db.Filter
import edu.ldubgd.authservice.db.FilterRepository
import edu.ldubgd.authservice.db.dto.FilterDTO
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FilterServiceImpl(
    val filterRepository: FilterRepository,
    val objectMapper: ObjectMapper = jacksonObjectMapper()
) : FilterService {

    override fun getFiltersByNames(names: List<String>): List<Filter> {
        return filterRepository.findFiltersByNameIn(names)
    }

    @Transactional
    override fun getFiltersFromDTO(filterDTO: Collection<FilterDTO>): List<Filter> {
        val result = mutableListOf<Filter>()
        val newFilters = mutableListOf<FilterDTO>()
        filterDTO.forEach { dto ->
            val filters = filterRepository.findFiltersByName(dto.name)
            if (filters.isNotEmpty()) {
                val alreadyExists = filters.find { dto.args == it.args }
                if (alreadyExists != null) {
                    result.add(alreadyExists)
                } else{
                    newFilters.add(dto)
                }
            } else{
                newFilters.add(dto)
            }
        }
        val saveNewFilters = saveFilters(newFilters)
        result.addAll(saveNewFilters)
        return result
    }

    override fun saveFilters(filters: Collection<FilterDTO>): List<Filter> {
        val result = mutableListOf<Filter>()
        filters.forEach { dto ->
            result.add(
                filterRepository.saveWithArgs(
                    name = dto.name,
                    args = objectMapper.writeValueAsString(dto.args)
                )
            )
        }
        return result
    }

}