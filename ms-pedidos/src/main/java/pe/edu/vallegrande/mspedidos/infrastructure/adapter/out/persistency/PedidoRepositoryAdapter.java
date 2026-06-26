package pe.edu.vallegrande.mspedidos.infrastructure.adapter.out.persistency;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pe.edu.vallegrande.mspedidos.application.port.out.IPedidoRepositoryPort;
import pe.edu.vallegrande.mspedidos.domain.model.Pedido;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class PedidoRepositoryAdapter implements IPedidoRepositoryPort {
    private  final PedidoRepository repository;

    @Override
    public Flux<Pedido> findAll() {
        return repository.findAll();
    }

    @Override
    public Mono<Pedido> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Mono<Pedido> save(Pedido order) {
        return repository.save(order);
    }
}
