package stand.catalog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import
import stand.support.ContractConfiguration

@SpringBootApplication
@Import(ContractConfiguration::class)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
