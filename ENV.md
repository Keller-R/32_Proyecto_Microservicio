# Variables de Entorno — Guía de uso

## ¿Qué es el archivo `.env`?

Un archivo `.env` es un archivo de texto plano donde se guardan las variables de entorno de una aplicación. En vez de escribir las credenciales directamente en el código, se leen desde este archivo. Así el código es el mismo para todos los entornos (local, Docker, Kubernetes) y solo cambian los valores del `.env`.

---

## ¿Cómo funciona en este proyecto?

El `application.yaml` de cada microservicio lee las variables así:

```yaml
# Ejemplo en ms-productos/src/main/resources/application.yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}

server:
  port: ${SERVER_PORT:8081}   # si no existe la variable, usa 8081 por defecto
```

`${NOMBRE_VARIABLE}` le dice a Spring: "busca esta variable en el entorno". Si no la encuentra y tiene un valor por defecto (`:8081`), usa ese.

---

## Archivos `.env.example`

Cada microservicio tiene un archivo `.env.example` que sirve como plantilla. Este archivo **sí se sube a GitHub** porque no tiene la contraseña real — solo muestra qué variables se necesitan:

### ms-productos/.env.example
```env
# Base de datos Supabase
DB_HOST=aws-1-us-east-2.pooler.supabase.com
DB_PORT=6543
DB_NAME=postgres
DB_SSL_MODE=require
DB_USER=postgres.xxzeitbtoohxknazssdu
DB_PASSWORD=tu-contraseña-aqui

# Servidor
SERVER_PORT=8081
```

### ms-pedidos/.env.example
```env
# Base de datos Supabase
DB_HOST=aws-1-us-east-2.pooler.supabase.com
DB_PORT=6543
DB_NAME=postgres
DB_SSL_MODE=require
DB_USER=postgres.xxzeitbtoohxknazssdu
DB_PASSWORD=tu-contraseña-aqui

# Servidor
SERVER_PORT=8082

# URL del microservicio ms-productos
PRODUCTOS_SERVICE_URL=http://localhost:8081
```

---

## ¿Cómo usar el `.env` según el entorno?

### Entorno Local (ejecutando con Maven)

**Paso 1** — Copiar el `.env.example` y crear tu `.env` real:
```bash
# En ms-productos
copy ms-productos\.env.example ms-productos\.env

# En ms-pedidos
copy ms-pedidos\.env.example ms-pedidos\.env
```

**Paso 2** — Editar el `.env` y colocar la contraseña real:
```env
DB_PASSWORD=Steven2005*123456-   # ← tu contraseña real aquí
```

**Paso 3** — Ejecutar el microservicio (Spring Boot carga el `.env` automáticamente si usas el plugin):
```bash
cd ms-productos
./mvnw spring-boot:run
```

> El archivo `.env` real **nunca se sube a GitHub** — está en el `.gitignore` para proteger las credenciales.

---

### Entorno Docker

Con Docker puedes pasar el `.env` directamente al contenedor con `--env-file`:

```bash
# Correr ms-productos pasando el .env
docker run -p 8081:8081 \
  --env-file ms-productos/.env \
  kellerr/ms-productos:v1

# Correr ms-pedidos pasando el .env
docker run -p 8082:8082 \
  --env-file ms-pedidos/.env \
  -e PRODUCTOS_SERVICE_URL=http://host.docker.internal:8081 \
  kellerr/ms-pedidos:v1
```

> `host.docker.internal` es la forma de que un contenedor Docker acceda a `localhost` de tu máquina.

También puedes usar `docker-compose` con un archivo `.env`:
```yaml
# docker-compose.yml (ejemplo)
services:
  ms-productos:
    image: kellerr/ms-productos:v1
    env_file:
      - ms-productos/.env
    ports:
      - "8081:8081"

  ms-pedidos:
    image: kellerr/ms-pedidos:v1
    env_file:
      - ms-pedidos/.env
    environment:
      - PRODUCTOS_SERVICE_URL=http://ms-productos:8081
    ports:
      - "8082:8082"
```

---

### Entorno Kubernetes

En Kubernetes **no se usa el `.env`** directamente. En su lugar se usa un **Secret** que cumple la misma función pero de forma segura dentro del cluster.

Los archivos `keller-rejas-32-secret-be.yml` reemplazan al `.env`:

```yaml
# k8s/ms-productos/keller-rejas-32-secret-be.yml
apiVersion: v1
kind: Secret
metadata:
  name: ms-productos-secret
  namespace: ms-productos-ns
type: Opaque
stringData:
  DB_HOST: "aws-1-us-east-2.pooler.supabase.com"
  DB_PORT: "5432"
  DB_NAME: "postgres"
  DB_SSL_MODE: "require"
  DB_USER: "postgres.xxzeitbtoohxknazssdu"
  DB_PASSWORD: "Steven2005*123456-"
  SERVER_PORT: "8081"
```

El Deployment las consume así:
```yaml
env:
- name: DB_PASSWORD
  valueFrom:
    secretKeyRef:
      name: ms-productos-secret   # nombre del Secret
      key: DB_PASSWORD             # clave dentro del Secret
```

---

## Resumen — ¿qué usar en cada entorno?

| Entorno | Cómo se pasan las variables |
|---|---|
| **Local (Maven)** | Archivo `.env` en la carpeta del microservicio |
| **Docker** | `--env-file .env` o `env_file` en docker-compose |
| **Kubernetes** | Secret (`keller-rejas-32-secret-be.yml`) |

---

## Regla de oro

| Archivo | ¿Se sube a GitHub? | ¿Por qué? |
|---|---|---|
| `.env` | ❌ NO | Tiene credenciales reales |
| `.env.example` | ✅ SÍ | Es una plantilla sin datos reales |
| `secret-be.yml` | ⚠️ Solo si el repo es privado | Tiene credenciales reales |
| `application.yaml` | ✅ SÍ | No tiene credenciales, solo referencias a variables |
