package pe.edu.vallegrande.msproductos.infrastructure.out.persistence;

import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import pe.edu.vallegrande.msproductos.domain.model.Producto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ProductoRespository extends ReactiveCrudRepository<Producto, Long> {
    Flux<Producto> findByActiveTrue();

    @Query("UPDATE productos SET stock = stock - :quantity WHERE id = :id AND stock >= :quantity RETURNING *")
    Mono<Producto> decreaseStock(Long id, Integer quantity);
}
