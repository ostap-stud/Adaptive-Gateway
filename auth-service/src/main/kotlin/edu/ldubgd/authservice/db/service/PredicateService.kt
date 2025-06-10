package edu.ldubgd.authservice.db.service

import edu.ldubgd.authservice.db.Predicate
import edu.ldubgd.authservice.db.dto.PredicateDTO

interface PredicateService {
    fun getPredicatesByNames(names: List<String>): List<Predicate>
    fun getPredicatesAndSaveNew(predicateDTO: Collection<PredicateDTO>) : List<Predicate>
    fun savePredicates(predicates: Collection<PredicateDTO>) : List<Predicate>
}