# ManteniMax — CMMS de mantenimiento industrial

Sistema Integral de Gestión de Mantenimiento (CMMS) de escritorio para **INDUSTRIAS MANTENIMAX S.A.C.**:
controla equipos, órdenes de trabajo, repuestos con kardex, planes preventivos, indicadores
de desempeño (disponibilidad, MTTR, costos) y reportes ejecutivos en Excel y PDF.

Desarrollado con el mismo stack que el proyecto docente `crud-persona-spring`
(Spring Boot + JavaFX + SQLite + AtlantaFX + Ikonli + OpenPDF + POI + Lombok), pero con
alcance y acabado superiores.

---

## Cómo ejecutar

**Desde IntelliJ IDEA**

1. Abrir el proyecto `cmms-mantenimiento` como proyecto Maven (JDK 17+; el proyecto está
   compilado con Java 24, funciona con 17 o superior).
2. Ejecutar la clase `com.mantenimiento.cmms.CmmsApplication`.
3. Alternativa: `mvn javafx:run`.

**Desde terminal**

```bash
cd cmms-mantenimiento
./mvnw.cmd javafx:run        # Windows
./mvnw.cmd test              # 54 pruebas (lógica, integración y carga de las 11 vistas FXML)
```

**Como aplicación de escritorio (ejecutable, doble clic)**

1. Abrir `dist\ManteniMax\ManteniMax.exe` — o el acceso directo **ManteniMax** del
   escritorio (icono propio).
2. La base `mantenimiento.db` queda junto al ejecutable (`dist\ManteniMax\`).
3. Para regenerar el paquete:

```bash
.\mvnw.cmd package -DskipTests
jpackage --type app-image --name ManteniMax --input build\staging ^
  --main-jar cmms-mantenimiento-1.0.0.jar --icon build\icono.ico --dest dist
```

La base `mantenimiento.db` (SQLite) se crea sola en la carpeta del proyecto (o junto al
ejecutable) y, en el primer arranque, se cargan datos de demostración de forma **idempotente**
(si ya hay datos no se duplica nada). El flag está en `application.properties`:
`app.seeder.enabled=true`.

## Usuarios de prueba

| Usuario  | Contraseña  | Rol        | Alcance                                          |
|----------|-------------|------------|--------------------------------------------------|
| `admin`  | `admin123`  | ADMIN      | Todo, incluida la gestión de usuarios            |
| `jefe`   | `jefe123`   | SUPERVISOR | Todo salvo usuarios                              |
| `carlos` | `carlos123` | TÉCNICO    | Dashboard y **solo sus** órdenes (lectura)       |
| `marta`  | `marta123`  | TÉCNICO    | Igual que Carlos, vinculada a otra ficha técnica |

Las contraseñas se guardan con SHA-256 + sal aleatoria por usuario (`sal:hash`).

## Módulos

- **Dashboard** — KPIs (disponibilidad, MTTR, costo del mes con variación %, órdenes vencidas),
  gráficos de área (costo mensual), dona (estados), barras (tipos), rankings de equipos más
  costosos, carga por técnico, alertas de stock y preventivos, y botón que genera las órdenes
  preventivas vencidas.
- **Órdenes de trabajo** — flujo ABIERTA → EN_EJECUCION → COMPLETADA/CANCELADA con validación
  de transiciones, chips de estado/prioridad, consumo de repuestos con stock suficiente,
  costos (mano de obra + repuestos), detalle lateral con pestañas, filtros por estado/tipo/
  técnico, PDF de la orden con firmas para taller.
- **Equipos** — CRUD con categoría, ubicación, criticidad, vida útil e historial de órdenes.
- **Repuestos** — CRUD, entradas/salidas con motivo, **kardex** completo, alertas de stock
  bajo/mínimo y devolución al quitar una línea de la orden.
- **Preventivo** — planes con frecuencia en días, próxima fecha y técnico; generación de
  órdenes en un clic cuando se vencen (la fecha avanza sola).
- **Técnicos** — fichas con especialidad, carga activa/completadas y vínculo con una cuenta
  de usuario.
- **Catálogos** — categorías de equipo, ubicaciones y proveedores en pestañas con un panel
  lateral conmutable.
- **Reportes** — periodo arbitrario → libro Excel de 5 hojas (resumen, órdenes, costos por
  equipo, inventario valorizado, cumplimiento preventivo) y reporte ejecutivo en PDF A4 con
  portada, KPIs y gráficos.
- **Usuarios** (solo ADMIN) — cuentas, roles, alta/baja sin perder historial y último acceso.
- **Global** — login con estados visibles, barra de comandos con navegación en píldoras,
  paleta de comandos (Ctrl+K), bitácora del agente (Ctrl+B), dock de actividad con
  pausa/cancelar, modo claro/oscuro, toasts estructurados y exportación pausable.

## Experiencia de usuario: "Centro de mando"

En lugar del típico menú lateral, la interfaz se organiza en un centro de mando con
cuatro principios de diseño de agentes aplicados a toda la aplicación:

1. **Transparencia** — la *bitácora del agente* (consola inferior, Ctrl+B) registra cada
   paso: qué hizo, con qué **fuente** (tabla/servicio), bajo qué **criterio** y cuánto
   tardó. Un aspecto AOP (`TrazasAspect`) traza toda consulta a los servicios sin
   instrumentar su código; las trazas se filtran por tipo (consulta/cálculo/decisión/
   aviso/error) y se copian o exportan para auditoría. Los KPI del dashboard declaran su
   origen (BD vs. calculado) y su **confianza** según el número de muestras.
2. **Control constante** — toda acción larga corre por `AgenteTareas`, un pipeline de
   pasos con **pausa** (solo entre pasos, sin correr datos a medias), **cancelación**
   (sin reiniciar nada) y override: el dock inferior muestra actividad, chip de origen,
   barra de progreso, pasos y los botones Pausar/Cancelar en todo momento. Las
   sugerencias del dashboard se pueden **aplicar, ignorar o restaurar**.
3. **Estado proactivo** — el dock anuncia "Consultando base de datos X…" antes de cada
   consulta, el login informa su avance, y los datos distinguibles (BD vs. calculado)
   se muestran con sufijos y confianza sin alarmar.
4. **Errores diseñados primero** — todo fallo de negocio pasa por `Diagnostico`, que lo
   estructura en **qué pasó / por qué / qué hacer** con un código (p. ej. `REP-004`),
   y `Notificador.fallar` muestra el bloque de error con *Copiar diagnóstico* y
   *Reintentar* (como toast compacto si hay un formulario abierto).

Extras de productividad: paleta de comandos difusa con Ctrl+K (navegar, cambiar tema,
limpiar bitácora, atajos…) y atajos Ctrl+K / Ctrl+B / Esc.

## Identidad visual, fotos y entrega

- **Logo propio** (`src/main/resources/img/logo.png`, 512 px): generado con la *misma*
  tipografía Feather que usan los íconos de la interfaz mediante `build\generar-iconos.ps1`
  (degrado azul, engranaje, rectángulo redondeado). Se usa en la barra de marca, en el
  login, en la ventana de JavaFX (`stage.getIcons()`), en el ejecutable y en el acceso
  directo (`build\icono.ico`, contenedor PNG de 16 a 256 px).
- **Fotos reales** (Unsplash, licencia libre) en `src/main/resources/img/`:
  - `foto-login.jpg` — panel izquierdo del login a dos paneles (marca + lema + chips).
  - `foto-dashboard.jpg` — hero con degradado oscuro sobre la cabecera del dashboard.
  - `equipos/` — una foto por categoría (mecánica, eléctrica, neumática, instrumentación,
    termohidráulica): miniatura redondeada en la columna EQUIPO de la tabla y foto con
    capota en la cabecera del drawer (cambia al elegir la categoría).
  - `repuestos/` — miniaturas por palabra clave (rodamiento, correa, filtro, aceite) con
    fotos de categoría como respaldo y foto genérica si no hay coincidencia.
- **Navegación sin residuo azul**: la cápsula `indicador` ya no estira a todo el ancho de
  la barra (se limita a su medida preferida, se recoloca tras el primer layout real y
  anima 260 ms hasta la píldora activa).
- **Render ágil al cambiar de vista**: las 8 vistas restantes se **precargan** de a una por
  pulso (150 ms) tras entrar, y la transición de navegación usa un fundido de 240 ms; el
  primer clic de cada sección es instantáneo.
- **Ejecutable**: `dist\ManteniMax\ManteniMax.exe` (imagen de aplicación con runtime
  propio) + acceso directo `ManteniMax.lnk` en el escritorio con el `.ico` del logo.

## Arquitectura

```
src/main/java/com/mantenimiento/cmms
├── CmmsApplication.java / Launcher.java / JavaFxApplication.java   arranque Spring + JavaFX
├── config/        datos de la empresa (razón social, RUC, dirección)
├── agente/         capa de experiencia: Trazador (bitácora), EstadoAgente (dock),
│                   AgenteTareas (pausa/cancelar por pasos), Recomendador,
│                   TrazasAspect (trazado AOP de servicios)
├── exception/      NegocioException + Diagnostico (qué pasó / por qué / qué hacer)
├── model/          11 entidades + 6 enums (validaciones jakarta)
├── repository/     Spring Data JPA (specifications + queries JPQL)
├── service/        Seguridad, Sesion, Usuario, Tecnico, Catalogo, Equipo,
│                   Repuesto, Orden (flujo), Preventivo, Dashboard, reportes
├── seeder/         DatabaseSeeder idempotente (40 equipos, 55 repuestos,
│                   150 órdenes en 8 meses, planes vencidos)
├── ui/             Notificador (errores estructurados + toasts), Formularios,
│                   Combos, Tablas, Paginador, Exportador (con pausa/cancelar), Fx
└── controller/     10 controladores (FXML) de las vistas
src/main/resources
├── view/*.fxml     11 vistas
└── css/app.css     tema AtlantaFX + clases propias
```

**Pruebas** (`src/test`): `SeguridadTest` (hash/sal), `OrdenFlujoTest` (ciclo de vida y
transiciones), `RepuestoStockTest` (stock/kardex/devoluciones), `PreventivoTest` (planes y
generación), `ReporteIntegracionTest` (seeder + Excel + PDF reales), `VistasFxmlTest`
(carga las 11 vistas con la fábrica de Spring: caza errores de `fx:id` y `onAction` en
lugar de romper en runtime) y la suite del agente: `TrazadorTest` (bitácora, límite,
exportación), `DiagnosticoTest` (estructura de errores y códigos), `AgenteTareasTest`
(pausa/cancelar/fallo con control del usuario), `TrazasAspectTest` (trazado AOP real) y
`RecomendadorTest` (sugerencias con fuente y criterio, descarte y restauración).

## Decisiones técnicas destacadas

- **SQLite sin servidor** con HikariCP `maximum-pool-size=1` (aplicación monousuario).
- El estado del equipo se deriva del flujo: al iniciar una orden pasa a *En mantenimiento*
  y al cerrarla vuelve a *Operativo* si no queda otra orden en curso.
- El precio del repuesto se **congela** en la línea de detalle; el costo de la orden es
  `mano de obra + Σ(cantidad × precioUnitario)`.
- Cada movimiento de stock deja kardex con saldo resultante; nunca negativo.
- `TecnicoController`/`OrdenService` resuelven el técnico de la sesión vía `usuario_id`
  para que un técnico solo vea y modifique sus órdenes.
