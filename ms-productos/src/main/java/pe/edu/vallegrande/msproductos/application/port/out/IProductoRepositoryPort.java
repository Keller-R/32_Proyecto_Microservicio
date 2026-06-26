package pe.edu.vallegrande.msproductos.application.port.out;

import org.springframework.stereotype.Repository;
import pe.edu.vallegrande.msproductos.domain.model.Producto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface IProductoRepositoryPort {

    Flux<Producto> findAllActive();
    Mono<Producto> findById(Long id);
    Mono<Producto> save(Producto product);
    Mono<Producto> decreaseStock(Long id, Integer quantity);
}
