package pe.edu.vallegrande.msproductos.application.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.vallegrande.msproductos.application.port.in.IProductoServicePort;
import pe.edu.vallegrande.msproductos.application.port.out.IProductoRepositoryPort;
import pe.edu.vallegrande.msproductos.domain.model.Producto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ProductoService implements IProductoServicePort {

    private final IProductoRepositoryPort repositoryPort;
    @Override
    public Flux<Producto> findAll() {
        return repositoryPort.findAllActive();
    }

    @Override
    public Mono<Producto> findById(Long id) {
        return repositoryPort.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado")));
    }

    @Override
    public Mono<Producto> create(Producto product) {
        product.setActive(true);
        return repositoryPort.save(product);
    }

    @Override
    public Mono<Producto> update(Long id, Producto product) {
        return repositoryPort.findById(id)
                .flatMap(existing -> {
                    existing.setName(product.getName());
                    existing.setPrice(product.getPrice());
                    existing.setStock(product.getStock());
                    return repositoryPort.save(existing);
                });
    }

    @Override
    public Mono<Void> delete(Long id) {
        return findById(id)
                .flatMap(p -> {
                    p.setActive(false);
                    return repositoryPort.save(p);
                })
                .then();
    }

    @Override
    public Mono<Producto> decreaseStock(Long id, Integer quantity) {
        return repositoryPort.decreaseStock(id, quantity)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Stock insuficiente")));
    }
}
