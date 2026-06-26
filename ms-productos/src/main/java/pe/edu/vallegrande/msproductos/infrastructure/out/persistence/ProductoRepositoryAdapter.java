package pe.edu.vallegrande.msproductos.infrastructure.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.edu.vallegrande.msproductos.application.port.out.IProductoRepositoryPort;
import pe.edu.vallegrande.msproductos.domain.model.Producto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ProductoRepositoryAdapter implements IProductoRepositoryPort {

    private final ProductoRespository repository;

    @Override
    public Flux<Producto> findAllActive() {
        return repository.findByActiveTrue();
    }

    @Override
    public Mono<Producto> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Mono<Producto> save(Producto product) {
        return repository.save(product);
    }

    @Override
    public Mono<Producto> decreaseStock(Long id, Integer quantity) {
        return repository.decreaseStock(id, quantity);
    }
}
