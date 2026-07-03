# Documentación del Proyecto — Microservicios ms-productos y ms-pedidos

## Índice
1. [Descripción General](#1-descripción-general)
2. [Arquitectura](#2-arquitectura)
3. [Estructura del Proyecto](#3-estructura-del-proyecto)
4. [Microservicio ms-productos](#4-microservicio-ms-productos)
5. [Microservicio ms-pedidos](#5-microservicio-ms-pedidos)
6. [Comunicación entre Microservicios](#6-comunicación-entre-microservicios)
7. [Dockerización](#7-dockerización)
8. [Kubernetes](#8-kubernetes)
9. [Variables de Entorno](#9-variables-de-entorno)
10. [Endpoints disponibles](#10-endpoints-disponibles)
11. [Flujo completo de un Pedido](#11-flujo-completo-de-un-pedido)
12. [Cómo ejecutar el proyecto](#12-cómo-ejecutar-el-proyecto)

---

## 1. Descripción General

Este proyecto consiste en dos microservicios backend desarrollados con **Spring Boot + WebFlux** (programación reactiva) que se comunican entre sí:

| Microservicio | Puerto | Responsabilidad |
|---|---|---|
| `ms-productos` | 8081 | Gestión del catálogo de productos y stock |
| `ms-pedidos` | 8082 | Gestión de pedidos, consume ms-productos |

Ambos usan:
- **Spring WebFlux** — API reactiva no bloqueante
- **R2DBC** — acceso reactivo a base de datos PostgreSQL
- **Supabase** — base de datos PostgreSQL en la nube
- **Docker** — contenedores para despliegue
- **Kubernetes** — orquestación de contenedores

---

## 2. Arquitectura

### Patrón Hexagonal (Ports & Adapters)

Cada microservicio sigue la arquitectura hexagonal, que separa el negocio de la infraestructura:

```
┌─────────────────────────────────────────────┐
│               INFRAESTRUCTURA               │
│                                             │
│  ┌─────────────┐         ┌───────────────┐  │
│  │  REST       │         │  Repository   │  │
│  │  Controller │         │  Adapter      │  │
│  │  (Adapter   │         │  (Adapter     │  │
│  │   IN)       │         │   OUT)        │  │
│  └──────┬──────┘         └───────┬───────┘  │
│         │                        │          │
│  ───────▼────── APLICACIÓN ──────▼───────   │
│  ┌──────────────────────────────────────┐   │
│  │         Puerto IN (Interface)        │   │
│  │         Service (lógica negocio)     │   │
│  │         Puerto OUT (Interface)       │   │
│  └──────────────────────────────────────┘   │
│                                             │
│  ──────────────── DOMINIO ────────────────  │
│  ┌──────────────────────────────────────┐   │
│  │         Modelos (Producto, Pedido)   │   │
│  └──────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

### Capas de cada microservicio

```
domain/
  model/          → Entidades del negocio (Producto, Pedido)

application/
  port/in/        → Interfaces que define lo que puede hacer el servicio
  port/out/       → Interfaces que define lo que necesita del exterior
  service/        → Lógica de negocio pura

infrastructure/
  adapter/in/     → REST Controllers (reciben peticiones HTTP)
  adapter/out/    → Repositorios BD, clientes HTTP a otros servicios
```

---

## 3. Estructura del Proyecto

```
32_Proyecto_Microservicio/
│
├── ms-productos/                          ← Microservicio de productos
│   ├── src/main/java/.../
│   │   ├── domain/model/
│   │   │   └── Producto.java
│   │   ├── application/
│   │   │   ├── port/in/IProductoServicePort.java
│   │   │   ├── port/out/IProductoRepositoryPort.java
│   │   │   └── service/ProductoService.java
│   │   └── infrastructure/
│   │       ├── adapter/in/rest/ProductoRest.java
│   │       └── adapter/out/persistence/
│   │           ├── ProductoRepositoryAdapter.java
│   │           └── ProductoRespository.java
│   ├── src/main/resources/application.yaml
│   ├── Dockerfile
│   └── pom.xml
│
├── ms-pedidos/                            ← Microservicio de pedidos
│   ├── src/main/java/.../
│   │   ├── domain/model/
│   │   │   ├── Pedido.java
│   │   │   └── Producto.java              ← Espejo del modelo (sin @Table)
│   │   ├── application/
│   │   │   ├── port/in/IPedidoServicePort.java
│   │   │   ├── port/out/IPedidoRepositoryPort.java
│   │   │   ├── port/out/IProductoClientPort.java
│   │   │   └── service/PedidoService.java
│   │   └── infrastructure/
│   │       ├── adapter/in/Rest/PedidoRest.java
│   │       └── adapter/out/
│   │           ├── persistence/PedidoRepositoryAdapter.java
│   │           └── client/ProductoClientAdapter.java  ← Llama a ms-productos
│   ├── src/main/resources/application.yaml
│   ├── Dockerfile
│   └── pom.xml
│
└── k8s/
    ├── ms-productos/                      ← Manifiestos Kubernetes BE
    │   ├── keller-rejas-32-namespace-be.yml
    │   ├── keller-rejas-32-secret-be.yml
    │   ├── keller-rejas-32-service-be.yml
    │   └── keller-rejas-32-deployment-be.yml
    └── ms-pedidos/                        ← Manifiestos Kubernetes BE
        ├── keller-rejas-32-namespace-be.yml
        ├── keller-rejas-32-secret-be.yml
        ├── keller-rejas-32-service-be.yml
        └── keller-rejas-32-deployment-be.yml
```

---

## 4. Microservicio ms-productos

### Modelo de dominio

```java
@Table(name = "productos")
public class Producto {
    private Long id;
    private String name;
    private Double price;
    private Integer stock;
    private Boolean active;   // soft-delete
}
```

### Operaciones disponibles

| Método | Puerto | Descripción |
|---|---|---|
| `findAll()` | IProductoServicePort | Lista solo productos activos |
| `findById(id)` | IProductoServicePort | Busca por ID, lanza 404 si no existe |
| `create(product)` | IProductoServicePort | Crea producto con active=true |
| `update(id, product)` | IProductoServicePort | Actualiza nombre, precio y stock |
| `delete(id)` | IProductoServicePort | Soft-delete (active=false) |
| `decreaseStock(id, qty)` | IProductoServicePort | Reduce stock de forma atómica en BD |

### Query atómico de stock

El método `decreaseStock` usa una query directamente en la base de datos para evitar condiciones de carrera entre peticiones concurrentes:

```sql
UPDATE productos
SET stock = stock - :quantity
WHERE id = :id AND stock >= :quantity
RETURNING *
```

Si no hay stock suficiente, la query no retorna nada y el `switchIfEmpty` lanza un error `400 Bad Request`.

---

## 5. Microservicio ms-pedidos

### Modelo de dominio

```java
@Table(name = "pedidos")
public class Pedido {
    private Long id;
    private String productId;   // ID del producto en ms-productos
    private Integer quantity;   // Cantidad solicitada
    private Double price;       // Precio unitario (copiado de ms-productos)
    private Double total;       // price * quantity
    private String status;      // CONFIRMADO | CANCELADO
    private LocalDateTime fecha;
}
```

### Operaciones disponibles

| Método | Descripción |
|---|---|
| `findAll()` | Lista todos los pedidos |
| `findById(id)` | Busca pedido por ID |
| `create(order)` | Crea pedido (valida stock, llama ms-productos) |
| `cancel(id)` | Cambia status a CANCELADO |

---

## 6. Comunicación entre Microservicios

### Diagrama de comunicación

```
  Cliente HTTP
      │
      ▼
┌─────────────┐    HTTP PATCH /api/productos/{id}/decreaseStock
│  ms-pedidos │ ─────────────────────────────────────────────► ┌──────────────┐
│  :8082      │                                                 │ ms-productos │
│             │ ◄───────────────────────────────────────────── │ :8081        │
└─────────────┘    Producto actualizado (con stock reducido)    └──────────────┘
      │                                                               │
      ▼                                                               ▼
  BD Supabase                                                    BD Supabase
  (tabla pedidos)                                               (tabla productos)
```

### Cómo se implementa

**1. Puerto de salida en ms-pedidos** — define el contrato:
```java
// application/port/out/IProductoClientPort.java
public interface IProductoClientPort {
    Mono<Producto> findById(Long id);
    Mono<Producto> decreaseStock(Long id, Integer quantity);
}
```

**2. Adapter que implementa la comunicación real** — usa Spring WebClient:
```java
// infrastructure/adapter/out/client/ProductoClientAdapter.java
@Component
public class ProductoClientAdapter implements IProductoClientPort {

    private final WebClient webClient;

    public ProductoClientAdapter(@Value("${servicios.productos-url}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public Mono<Producto> findById(Long id) {
        return webClient.get()
                .uri("/api/productos/{id}", id)
                .retrieve()
                .bodyToMono(Producto.class);
    }

    @Override
    public Mono<Producto> decreaseStock(Long id, Integer quantity) {
        return webClient.patch()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/productos/{id}/decreaseStock")
                        .queryParam("quantity", quantity)
                        .build(id))
                .retrieve()
                .bodyToMono(Producto.class);
    }
}
```

**3. URL del servicio — configuración en application.yaml:**
```yaml
# ms-pedidos/src/main/resources/application.yaml
servicios:
  productos-url: ${PRODUCTOS_SERVICE_URL:http://localhost:8081}
```

La URL se inyecta por variable de entorno. Cambia según el entorno:

| Entorno | Valor de PRODUCTOS_SERVICE_URL |
|---|---|
| Local | `http://localhost:8081` |
| Kubernetes | `http://ms-productos-service.ms-productos-ns.svc.cluster.local:80` |

**4. Lógica de negocio en PedidoService** — orquesta la creación de un pedido:
```java
public Mono<Pedido> create(Pedido order) {
    return productoClientPort.findById(Long.valueOf(order.getProductId()))
        .switchIfEmpty(Mono.error(new ResponseStatusException(NOT_FOUND, "Producto no encontrado")))
        .flatMap(product -> {
            // Validación previa de stock
            if (product.getStock() < order.getQuantity()) {
                return Mono.error(new ResponseStatusException(BAD_REQUEST, "Stock insuficiente"));
            }
            // Reduce el stock en ms-productos (operación atómica en BD)
            return productoClientPort.decreaseStock(Long.valueOf(order.getProductId()), order.getQuantity())
                .flatMap(updated -> {
                    order.setPrice(product.getPrice());
                    order.setTotal(product.getPrice() * order.getQuantity());
                    order.setStatus("CONFIRMADO");
                    order.setFecha(LocalDateTime.now());
                    return repositoryPort.save(order);
                });
        });
}
```

---

## 7. Dockerización

Ambos servicios usan un **Dockerfile multi-stage** para no depender de la carpeta `target` local:

```dockerfile
# Etapa 1: compilar con Maven
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa 2: imagen final ligera (100-200MB)
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081   # (8082 en ms-pedidos)
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Ventajas del multi-stage:
- La imagen final no incluye Maven ni el código fuente
- Solo contiene el JRE y el JAR compilado
- Tamaño final estimado: **150-200MB**

### Construir y subir las imágenes:

```bash
# ms-productos
docker build -t kellerr/ms-productos:v1 ./ms-productos
docker push kellerr/ms-productos:v1

# ms-pedidos
docker build -t kellerr/ms-pedidos:v1 ./ms-pedidos
docker push kellerr/ms-pedidos:v1
```

---

## 8. Kubernetes

### Estructura de namespaces

Cada microservicio se despliega en su propio namespace aislado:

```
Cluster Kubernetes
├── Namespace: ms-productos-ns
│   ├── Secret: ms-productos-secret       ← variables de entorno
│   ├── Service: ms-productos-service     ← ClusterIP, puerto 80 → 8081
│   └── Deployment: ms-productos-deployment
│       ├── Pod 1 (replica)
│       └── Pod 2 (replica)
│
└── Namespace: ms-pedidos-ns
    ├── Secret: ms-pedidos-secret         ← variables de entorno
    ├── Service: ms-pedidos-service       ← LoadBalancer, puerto 8080 → 8082
    └── Deployment: ms-pedidos-deployment
        ├── Pod 1 (replica)
        └── Pod 2 (replica)
```

### Descripción de cada archivo

**namespace-be.yml** — crea el espacio aislado:
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: ms-productos-ns
```

**secret-be.yml** — almacena credenciales de forma segura:
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: ms-productos-secret
  namespace: ms-productos-ns
type: Opaque
stringData:
  DB_PASSWORD: "tu-contraseña"
  # ... resto de variables
```

**service-be.yml** — expone el deployment dentro/fuera del cluster:
```yaml
# ms-productos usa ClusterIP (solo accesible dentro del cluster)
type: ClusterIP

# ms-pedidos usa LoadBalancer (accesible desde fuera)
type: LoadBalancer
```

**deployment-be.yml** — define cómo corren los pods:
```yaml
spec:
  replicas: 2          # siempre 2 pods corriendo
  containers:
  - image: kellerr/ms-productos:v1
    env:
    - name: DB_PASSWORD
      valueFrom:
        secretKeyRef:  # lee del Secret, no hardcodeado
          name: ms-productos-secret
          key: DB_PASSWORD
    readinessProbe:    # Kubernetes sabe cuándo el pod está listo
      httpGet:
        path: /actuator/health/readiness
        port: 8081
    livenessProbe:     # Kubernetes reinicia el pod si deja de responder
      httpGet:
        path: /actuator/health/liveness
        port: 8081
```

### Comunicación cross-namespace en Kubernetes

Cuando ms-pedidos necesita llamar a ms-productos (que está en otro namespace), usa el DNS interno completo de Kubernetes:

```
http://ms-productos-service.ms-productos-ns.svc.cluster.local:80
         └──────────────┘  └─────────────┘  └──────────────┘
           nombre service    namespace          dominio k8s
```

Esto se configura en el Secret de ms-pedidos:
```yaml
PRODUCTOS_SERVICE_URL: "http://ms-productos-service.ms-productos-ns.svc.cluster.local:80"
```

### Aplicar los manifiestos (orden importante)

```bash
# 1. Primero ms-productos (BE que es llamado)
kubectl apply -f k8s/ms-productos/keller-rejas-32-namespace-be.yml
kubectl apply -f k8s/ms-productos/keller-rejas-32-secret-be.yml
kubectl apply -f k8s/ms-productos/keller-rejas-32-service-be.yml
kubectl apply -f k8s/ms-productos/keller-rejas-32-deployment-be.yml

# 2. Luego ms-pedidos (el que consume ms-productos)
kubectl apply -f k8s/ms-pedidos/keller-rejas-32-namespace-be.yml
kubectl apply -f k8s/ms-pedidos/keller-rejas-32-secret-be.yml
kubectl apply -f k8s/ms-pedidos/keller-rejas-32-service-be.yml
kubectl apply -f k8s/ms-pedidos/keller-rejas-32-deployment-be.yml
```

### Comandos útiles

```bash
# Ver pods corriendo
kubectl get pods -n ms-productos-ns
kubectl get pods -n ms-pedidos-ns

# Ver servicios e IPs
kubectl get service -n ms-productos-ns
kubectl get service -n ms-pedidos-ns

# Ver logs de un pod
kubectl logs -n ms-productos-ns deployment/ms-productos-deployment
kubectl logs -n ms-pedidos-ns deployment/ms-pedidos-deployment

# Reiniciar pods (para aplicar nuevos secrets)
kubectl rollout restart deployment ms-productos-deployment -n ms-productos-ns
kubectl rollout restart deployment ms-pedidos-deployment -n ms-pedidos-ns

# Probar comunicación cross-namespace desde dentro del cluster
kubectl run test-curl --image=curlimages/curl --restart=Never --rm -it \
  -n ms-pedidos-ns -- curl http://ms-productos-service.ms-productos-ns.svc.cluster.local:80/api/productos
```

---

## 9. Variables de Entorno

### ms-productos

| Variable | Descripción | Default |
|---|---|---|
| `SERVER_PORT` | Puerto del servidor | `8081` |
| `DB_HOST` | Host de la base de datos | `aws-1-us-east-2.pooler.supabase.com` |
| `DB_PORT` | Puerto de la base de datos | `5432` |
| `DB_NAME` | Nombre de la base de datos | `postgres` |
| `DB_USER` | Usuario de la base de datos | — |
| `DB_PASSWORD` | Contraseña de la base de datos | — |
| `DB_SSL_MODE` | Modo SSL | `require` |

### ms-pedidos

Mismas variables que ms-productos más:

| Variable | Descripción | Default |
|---|---|---|
| `SERVER_PORT` | Puerto del servidor | `8082` |
| `PRODUCTOS_SERVICE_URL` | URL base de ms-productos | `http://localhost:8081` |

---

## 10. Endpoints disponibles

### ms-productos — `http://localhost:8081`

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/productos` | Lista todos los productos activos |
| GET | `/api/productos/{id}` | Obtiene un producto por ID |
| POST | `/api/productos` | Crea un nuevo producto |
| PUT | `/api/productos/{id}` | Actualiza un producto |
| DELETE | `/api/productos/{id}` | Elimina (soft-delete) un producto |
| PATCH | `/api/productos/{id}/decreaseStock?quantity=N` | Reduce stock en N unidades |

### ms-pedidos — `http://localhost:8082`

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/pedidos` | Lista todos los pedidos |
| GET | `/api/pedidos/{id}` | Obtiene un pedido por ID |
| POST | `/api/pedidos` | Crea un nuevo pedido |
| PATCH | `/api/pedidos/{id}/cancel` | Cancela un pedido |

---

## 11. Flujo completo de un Pedido

Cuando un cliente hace `POST /api/pedidos`:

```
Cliente
  │
  │  POST /api/pedidos
  │  { "productId": "1", "quantity": 2 }
  ▼
PedidoRest (Controller)
  │  llama a servicePort.create(order)
  ▼
PedidoService (Lógica de negocio)
  │
  ├─1─► GET /api/productos/1  ──────────────► ms-productos
  │     ◄── { id:1, price:50, stock:10 } ────
  │
  ├─2─► Valida: stock(10) >= quantity(2) ✅
  │
  ├─3─► PATCH /api/productos/1/decreaseStock?quantity=2 ──► ms-productos
  │     ◄── { id:1, price:50, stock:8 } ── (stock reducido atómicamente en BD)
  │
  ├─4─► Calcula: price=50, total=100, status="CONFIRMADO", fecha=now()
  │
  └─5─► Guarda pedido en BD (tabla pedidos)
          ◄── { id:1, productId:"1", quantity:2, price:50, total:100, status:"CONFIRMADO" }
  │
  ▼
Cliente recibe respuesta 201 Created
```

---

## 12. Cómo ejecutar el proyecto

### Opción A — Local con Java

```bash
# Terminal 1: ms-productos
cd ms-productos
cp .env.example .env   # editar con tus credenciales reales
./mvnw spring-boot:run

# Terminal 2: ms-pedidos
cd ms-pedidos
cp .env.example .env   # editar con tus credenciales reales
./mvnw spring-boot:run
```

### Opción B — Docker

```bash
# Construir imágenes
docker build -t kellerr/ms-productos:v1 ./ms-productos
docker build -t kellerr/ms-pedidos:v1 ./ms-pedidos

# Correr contenedores
docker run -p 8081:8081 --env-file ms-productos/.env kellerr/ms-productos:v1
docker run -p 8082:8082 --env-file ms-pedidos/.env \
  -e PRODUCTOS_SERVICE_URL=http://host.docker.internal:8081 \
  kellerr/ms-pedidos:v1
```

### Opción C — Kubernetes (Docker Desktop)

```bash
# Aplicar manifiestos en orden
kubectl apply -f k8s/ms-productos/keller-rejas-32-namespace-be.yml
kubectl apply -f k8s/ms-productos/keller-rejas-32-secret-be.yml
kubectl apply -f k8s/ms-productos/keller-rejas-32-service-be.yml
kubectl apply -f k8s/ms-productos/keller-rejas-32-deployment-be.yml

kubectl apply -f k8s/ms-pedidos/keller-rejas-32-namespace-be.yml
kubectl apply -f k8s/ms-pedidos/keller-rejas-32-secret-be.yml
kubectl apply -f k8s/ms-pedidos/keller-rejas-32-service-be.yml
kubectl apply -f k8s/ms-pedidos/keller-rejas-32-deployment-be.yml

# Verificar
kubectl get pods -n ms-productos-ns
kubectl get pods -n ms-pedidos-ns

# Acceder (LoadBalancer en Docker Desktop expone en localhost)
curl http://localhost:8080/api/pedidos
curl http://localhost:8080/api/pedidos -X POST \
  -H "Content-Type: application/json" \
  -d '{"productId":"1","quantity":2}'
```
