package edu.ldubgd.authservice.db.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import edu.ldubgd.authservice.db.Predicate
import edu.ldubgd.authservice.db.PredicateRepository
import edu.ldubgd.authservice.db.dto.PredicateDTO
import org.springframework.stereotype.Service

@Service
class PredicateServiceImpl(
    val predicateRepository: PredicateRepository,
    val objectMapper: ObjectMapper = jacksonObjectMapper()
) : PredicateService {

    override fun getPredicatesByName(name: String): List<Predicate> {
        return predicateRepository.findPredicatesByName(name)
    }

    override fun getPredicatesByNames(names: List<String>): List<Predicate> {
        return predicateRepository.findPredicatesByNameIn(names)
    }

    override fun getPredicatesAndSaveNew(predicateDTO: Collection<PredicateDTO>): List<Predicate> {
        val result = mutableListOf<Predicate>()
        val newPredicates = mutableListOf<PredicateDTO>()
        predicateDTO.forEach { dto ->
            val predicates = predicateRepository.findPredicatesByName(dto.name)
            if (predicates.isNotEmpty()) {
                val alreadyExists = predicates.find { dto.args == it.args }
                if (alreadyExists != null) {
                    result.add(alreadyExists)
                } else{
                    newPredicates.add(dto)
                }
            } else{
                newPredicates.add(dto)
            }
        }
        val saveNewPredicates = savePredicates(newPredicates)
        result.addAll(saveNewPredicates)
        return result
    }

    override fun savePredicates(predicates: Collection<PredicateDTO>): List<Predicate> {
        val result = mutableListOf<Predicate>()
        predicates.forEach { dto ->
            result.add(
                predicateRepository.saveWithArgs(
                    name = dto.name,
                    args = objectMapper.writeValueAsString(dto.args)
                )
            )
        }
        return result
    }

}