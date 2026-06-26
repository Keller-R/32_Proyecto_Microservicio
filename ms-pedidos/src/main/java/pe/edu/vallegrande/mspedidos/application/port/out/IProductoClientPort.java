package pe.edu.vallegrande.mspedidos.application.port.out;

import pe.edu.vallegrande.mspedidos.domain.model.Pedido;
import pe.edu.vallegrande.mspedidos.domain.model.Producto;
import reactor.core.publisher.Mono;


public interface IProductoClientPort {
    Mono<Producto> findById(Long id);
    Mono<Producto> decreaseStock(Long id, Integer quantity);
}
