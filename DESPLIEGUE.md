# Guía de Despliegue

## Requisitos previos

Tener instalado en tu máquina:
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (incluye Docker y Kubernetes)
- Cuenta en [Docker Hub](https://hub.docker.com/)
- kubectl (viene incluido con Docker Desktop)

Verificar que todo esté instalado:
```bash
docker --version
kubectl version --client
```

---

## Parte 1 — Construir las imágenes Docker

Desde la raíz del proyecto (`32_Proyecto_Microservicio/`), ejecuta:

```bash
# Construir imagen de ms-productos
docker build -t kellerr/ms-productos:v1 ./ms-productos

# Construir imagen de ms-pedidos
docker build -t kellerr/ms-pedidos:v1 ./ms-pedidos
```

Verificar que las imágenes se crearon correctamente:
```bash
docker images
```

Deberías ver algo así:
```
REPOSITORY              TAG    IMAGE ID       SIZE
kellerr/ms-productos    v1     abc123...      ~170MB
kellerr/ms-pedidos      v1     def456...      ~170MB
```

> El tamaño debe estar entre 100MB y 300MB gracias al multi-stage build con `eclipse-temurin:17-jre-alpine`.

---

## Parte 2 — Subir las imágenes a Docker Hub

**Paso 1** — Iniciar sesión en Docker Hub:
```bash
docker login
```
Te pedirá tu usuario y contraseña de Docker Hub.

**Paso 2** — Subir las imágenes:
```bash
docker push kellerr/ms-productos:v1
docker push kellerr/ms-pedidos:v1
```

**Paso 3** — Verificar en Docker Hub:

Entra a `https://hub.docker.com/u/kellerr` y deberías ver los dos repositorios publicados:
- `kellerr/ms-productos`
- `kellerr/ms-pedidos`

---

## Parte 3 — Despliegue en Kubernetes

### Antes de aplicar — actualizar la contraseña

Abre los dos archivos de secret y reemplaza `tu-contraseña-aqui` con la contraseña real de Supabase:

- `k8s/ms-productos/keller-rejas-32-secret-be.yml`
- `k8s/ms-pedidos/keller-rejas-32-secret-be.yml`

```yaml
# Busca esta línea en ambos archivos y cambia el valor:
DB_PASSWORD: "tu-contraseña-aqui"   # ← reemplazar aquí
```

### Aplicar los manifiestos

> Importante: ms-productos debe desplegarse primero porque ms-pedidos depende de él.

```bash
# --- ms-productos ---
kubectl apply -f k8s/ms-productos/keller-rejas-32-namespace-be.yml
kubectl apply -f k8s/ms-productos/keller-rejas-32-secret-be.yml
kubectl apply -f k8s/ms-productos/keller-rejas-32-service-be.yml
kubectl apply -f k8s/ms-productos/keller-rejas-32-deployment-be.yml

# --- ms-pedidos ---
kubectl apply -f k8s/ms-pedidos/keller-rejas-32-namespace-be.yml
kubectl apply -f k8s/ms-pedidos/keller-rejas-32-secret-be.yml
kubectl apply -f k8s/ms-pedidos/keller-rejas-32-service-be.yml
kubectl apply -f k8s/ms-pedidos/keller-rejas-32-deployment-be.yml
```

### Verificar que los pods están corriendo

```bash
kubectl get pods -n ms-productos-ns
kubectl get pods -n ms-pedidos-ns
```

Resultado esperado (STATUS = Running, READY = 1/1):
```
NAME                                       READY   STATUS    RESTARTS   AGE
ms-productos-deployment-796f85d749-9tlh8   1/1     Running   0          1m
ms-productos-deployment-796f85d749-vlsl5   1/1     Running   0          1m

NAME                                     READY   STATUS    RESTARTS   AGE
ms-pedidos-deployment-57cf97d7f9-dp9z4   1/1     Running   0          1m
ms-pedidos-deployment-57cf97d7f9-mk4dv   1/1     Running   0          1m
```

### Verificar los servicios

```bash
kubectl get service -n ms-productos-ns
kubectl get service -n ms-pedidos-ns
```

Resultado esperado:
```
NAME                   TYPE        CLUSTER-IP     PORT(S)
ms-productos-service   ClusterIP   10.96.x.x      80/TCP

NAME                 TYPE           CLUSTER-IP    EXTERNAL-IP   PORT(S)
ms-pedidos-service   LoadBalancer   10.96.x.x     localhost     8080:xxxxx/TCP
```

ms-pedidos tiene `EXTERNAL-IP: localhost` porque estás en Docker Desktop — significa que puedes acceder desde tu máquina.

---

## Parte 4 — Probar que todo funciona

### Probar ms-productos (desde dentro del cluster)

```bash
kubectl run test-curl --image=curlimages/curl --restart=Never --rm -it \
  -n ms-pedidos-ns -- \
  curl http://ms-productos-service.ms-productos-ns.svc.cluster.local:80/api/productos
```

Deberías recibir un JSON con la lista de productos.

### Probar ms-pedidos (desde fuera, tu máquina)

Listar pedidos:
```bash
curl http://localhost:8080/api/pedidos
```

Crear un pedido:
```bash
curl -X POST http://localhost:8080/api/pedidos \
  -H "Content-Type: application/json" \
  -d "{\"productId\":\"1\", \"quantity\":2}"
```

Respuesta esperada (`201 Created`):
```json
{
  "id": 1,
  "productId": "1",
  "quantity": 2,
  "price": 50.0,
  "total": 100.0,
  "status": "CONFIRMADO",
  "fecha": "2026-07-02T10:00:00"
}
```

Cancelar un pedido:
```bash
curl -X PATCH http://localhost:8080/api/pedidos/1/cancel
```

---

## Parte 5 — Si necesitas actualizar algo

### Actualizar la contraseña en los secrets

```bash
# 1. Editar el archivo secret y guardar
# 2. Re-aplicar solo el secret
kubectl apply -f k8s/ms-productos/keller-rejas-32-secret-be.yml
kubectl apply -f k8s/ms-pedidos/keller-rejas-32-secret-be.yml

# 3. Reiniciar los pods para que tomen el nuevo valor
kubectl rollout restart deployment ms-productos-deployment -n ms-productos-ns
kubectl rollout restart deployment ms-pedidos-deployment -n ms-pedidos-ns
```

### Actualizar la imagen (nueva versión)

```bash
# 1. Construir con nuevo tag
docker build -t kellerr/ms-productos:v2 ./ms-productos
docker push kellerr/ms-productos:v2

# 2. Actualizar el deployment
kubectl set image deployment/ms-productos-deployment \
  ms-productos=kellerr/ms-productos:v2 \
  -n ms-productos-ns
```

### Ver logs si algo falla

```bash
# Ver logs de ms-productos
kubectl logs -n ms-productos-ns deployment/ms-productos-deployment

# Ver logs de ms-pedidos
kubectl logs -n ms-pedidos-ns deployment/ms-pedidos-deployment

# Ver logs en tiempo real
kubectl logs -n ms-pedidos-ns deployment/ms-pedidos-deployment -f
```

### Eliminar todo (limpiar el cluster)

```bash
kubectl delete namespace ms-productos-ns
kubectl delete namespace ms-pedidos-ns
```

> Esto elimina todos los recursos (pods, services, secrets, deployments) dentro de cada namespace.
