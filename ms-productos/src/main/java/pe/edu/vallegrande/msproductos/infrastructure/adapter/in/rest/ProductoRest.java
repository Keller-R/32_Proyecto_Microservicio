package pe.edu.vallegrande.msproductos.infrastructure.adapter.in.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.edu.vallegrande.msproductos.application.port.in.IProductoServicePort;
import pe.edu.vallegrande.msproductos.domain.model.Producto;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/productos")
@RequiredArgsConstructor
public class ProductoRest {

    private final IProductoServicePort servicePort;

    @GetMapping
    public Flux<Producto> findAll() {
        return servicePort.findAll();
    }

    @GetMapping("/{id}")
    public Mono<Producto> findById(@PathVariable Long id) {
        return servicePort.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Producto> create(@RequestBody Producto product) {
        return servicePort.create(product);
    }

    @PutMapping("/{id}")
    public Mono<Producto> update(@PathVariable Long id, @RequestBody Producto product) {
        return servicePort.update(id, product);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable Long id) {
        return servicePort.delete(id);
    }

    @PatchMapping("/{id}/decreaseStock")
    public Mono<Producto> decreaseStock(@PathVariable Long id, @RequestParam Integer quantity) {
        return servicePort.decreaseStock(id, quantity);
    }
}
