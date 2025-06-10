package edu.ldubgd.authservice.db.service

import edu.ldubgd.authservice.db.Filter
import edu.ldubgd.authservice.db.dto.FilterDTO

interface FilterService {
    fun getFiltersByNames(names: List<String>): List<Filter>
    fun getFiltersFromDTO(filterDTO: Collection<FilterDTO>) : List<Filter>
    fun saveFilters(filters: Collection<FilterDTO>) : List<Filter>
}