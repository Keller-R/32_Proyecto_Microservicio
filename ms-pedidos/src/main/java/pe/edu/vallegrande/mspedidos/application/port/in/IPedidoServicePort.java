package pe.edu.vallegrande.mspedidos.application.port.in;

import pe.edu.vallegrande.mspedidos.domain.model.Pedido;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface IPedidoServicePort {
    Flux<Pedido> findALl();
    Mono<Pedido> findById(Long id);
    Mono<Pedido> create(Pedido order);
    Mono<Pedido> cancel(Long id);
}
