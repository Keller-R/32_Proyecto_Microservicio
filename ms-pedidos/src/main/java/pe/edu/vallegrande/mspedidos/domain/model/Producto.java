package pe.edu.vallegrande.mspedidos.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Producto {
    @Id
    private Long id;
    private String name;
    private Double price;
    private Integer stock;
    private Boolean active;
}
