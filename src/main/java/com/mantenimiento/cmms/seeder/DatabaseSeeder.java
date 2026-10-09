package com.mantenimiento.cmms.seeder;

import com.mantenimiento.cmms.model.*;
import com.mantenimiento.cmms.repository.*;
import com.mantenimiento.cmms.service.Seguridad;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Carga inicial de datos de demostracion. Es idempotente: si ya existen
 * cuentas de usuario no vuelve a ejecutarse, por lo que es seguro tenerlo
 * activo en cada arranque.
 *
 * <p>Poblacion: usuarios y roles, tecnicos, catalogos, 40 equipos,
 * 55 repuestos con kardex, 150 ordenes de trabajo repartidas en los ultimos
 * 8 meses y planes preventivos con algunas fechas vencidas.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final TecnicoRepository tecnicoRepository;
    private final CategoriaEquipoRepository categoriaRepository;
    private final UbicacionRepository ubicacionRepository;
    private final ProveedorRepository proveedorRepository;
    private final EquipoRepository equipoRepository;
    private final RepuestoRepository repuestoRepository;
    private final OrdenTrabajoRepository ordenRepository;
    private final PlanPreventivoRepository planRepository;
    private final MovimientoRepuestoRepository movimientoRepository;

    @Value("${app.seeder.enabled:false}")
    private boolean habilitado;

    @Override
    @Transactional
    public void run(String... args) {
        if (!habilitado) {
            log.info("Seeder desactivado (app.seeder.enabled=false).");
            return;
        }
        if (usuarioRepository.count() > 0) {
            log.info("Seeder: ya hay datos cargados, no se duplica nada.");
            return;
        }

        Random r = new Random(20261008L);
        LocalDate hoy = LocalDate.now();

        List<Usuario> usuarios = sembrarUsuarios();
        List<Tecnico> tecnicos = sembrarTecnicos(usuarios);
        List<CategoriaEquipo> categorias = sembrarCategorias();
        List<Ubicacion> ubicaciones = sembrarUbicaciones();
        List<Proveedor> proveedores = sembrarProveedores();
        List<Equipo> equipos = sembrarEquipos(categorias, ubicaciones, r, hoy);
        List<Repuesto> repuestos = sembrarRepuestos(proveedores, r);
        sembrarOrdenes(equipos, tecnicos, repuestos, r, hoy);
        sembrarPlanes(equipos, tecnicos, r, hoy);
        sembrarKardexInicial(repuestos, r, hoy);

        log.info("Seeder: {} usuarios, {} tecnicos, {} equipos, {} repuestos listos.",
                usuarios.size(), tecnicos.size(), equipos.size(), repuestos.size());
    }

    // ------------------------------------------------------------ usuarios

    private List<Usuario> sembrarUsuarios() {
        List<Usuario> usuarios = List.of(
                guardarUsuario("admin", "admin123", "Administrador de Sistema",
                        "admin@mantenimax.pe", Rol.ADMIN),
                guardarUsuario("jefe", "jefe123", "Jorge Salazar",
                        "jorge.salazar@mantenimax.pe", Rol.SUPERVISOR),
                guardarUsuario("carlos", "carlos123", "Carlos Rodriguez",
                        "carlos.rodriguez@mantenimax.pe", Rol.TECNICO),
                guardarUsuario("marta", "marta123", "Marta Quispe",
                        "marta.quispe@mantenimax.pe", Rol.TECNICO));
        return new ArrayList<>(usuarios);
    }

    private Usuario guardarUsuario(String login, String clave, String nombre,
                                   String email, Rol rol) {
        return usuarioRepository.save(Usuario.builder()
                .usuario(login)
                .contrasena(Seguridad.hashear(clave))
                .nombre(nombre)
                .email(email)
                .rol(rol)
                .activo(true)
                .build());
    }

    // ------------------------------------------------------------ tecnicos

    private List<Tecnico> sembrarTecnicos(List<Usuario> usuarios) {
        List<Tecnico> lista = new ArrayList<>();
        lista.add(guardarTecnico("45123678", "Carlos Rodriguez", "Mecanica industrial",
                "999888777", "carlos.rodriguez@mantenimax.pe", usuarios.get(2)));
        lista.add(guardarTecnico("46234567", "Jorge Vega", "Electricidad industrial",
                "999777666", "jorge.vega@mantenimax.pe", usuarios.get(3)));
        lista.add(guardarTecnico("47345678", "Marta Quispe", "Instrumentacion y control",
                "999666555", null, null));
        lista.add(guardarTecnico("48456789", "Luis Tomala", "Neumatica e hidraulica",
                "999555444", "luis.tomala@mantenimax.pe", null));
        return lista;
    }

    private Tecnico guardarTecnico(String dni, String nombre, String esp,
                                   String tel, String email, Usuario usuario) {
        return tecnicoRepository.save(Tecnico.builder()
                .dni(dni)
                .nombre(nombre)
                .especialidad(esp)
                .telefono(tel)
                .email(email)
                .usuario(usuario)
                .activo(true)
                .build());
    }

    // ------------------------------------------------------------ catalogos

    private List<CategoriaEquipo> sembrarCategorias() {
        String[][] datos = {
                {"Mecanica", "Motores, bombas, transmisiones y estructuras", "#2a78d6"},
                {"Electrica", "Motore, tableros, protecciones y alumbrado", "#e8a33d"},
                {"Neumatica", "Compresores, cilindros y valvulas de aire", "#3fb27f"},
                {"Instrumentacion", "Sensores, transmisores y lazos de control", "#9d6bd8"},
                {"Termohidraulica", "Calderas, intercambiadores y servicios de vapor", "#d6455f"}
        };
        List<CategoriaEquipo> lista = new ArrayList<>();
        for (String[] d : datos) {
            lista.add(categoriaRepository.save(CategoriaEquipo.builder()
                    .nombre(d[0]).descripcion(d[1]).color(d[2]).build()));
        }
        return lista;
    }

    private List<Ubicacion> sembrarUbicaciones() {
        String[][] datos = {
                {"Sala de compresores", "Planta baja, costado oeste"},
                {"Linea de empaque 1", "Planta baja, zona de envasado"},
                {"Linea de empaque 2", "Planta baja, zona de etiquetado"},
                {"Nave de produccion", "Planta alta, centro"},
                {"Sala electrica principal", "Planta baja, junto a subestacion"},
                {"Patio de maniobras", "Exterior, cerca de andenes"}
        };
        List<Ubicacion> lista = new ArrayList<>();
        for (String[] d : datos) {
            lista.add(ubicacionRepository.save(Ubicacion.builder()
                    .nombre(d[0]).descripcion(d[1]).build()));
        }
        return lista;
    }

    private List<Proveedor> sembrarProveedores() {
        String[][] datos = {
                {"Repuestos Andinos S.A.C.", "20512345678", "945123789", "ventas@repuestosandinos.pe"},
                {"Suministros Industriales Gamarra E.I.R.L.", "20456789012", "932165401", "contacto@gamarrasumi.pe"},
                {"Servicios Tecnicos del Sur S.A.", "20678901234", "954451122", "operaciones@stsur.com.pe"},
                {"Electricidad y Control Peru S.A.C.", "20345678901", "961899001", "ventas@elcontrol.pe"}
        };
        List<Proveedor> lista = new ArrayList<>();
        for (String[] d : datos) {
            lista.add(proveedorRepository.save(Proveedor.builder()
                    .nombre(d[0]).ruc(d[1]).telefono(d[2]).email(d[3]).build()));
        }
        return lista;
    }

    // -------------------------------------------------------------- equipos

    /** {nombre, categoria, ubicacion, critico} */
    private static final String[][] EQUIPOS_BASE = {
            {"Compresor de aire tornillo", "0", "0", "S"},
            {"Compresor de aire alternativo", "0", "0", "N"},
            {"Bomba centrifuga serie 3196", "0", "3", "S"},
            {"Bomba de desazufre", "0", "3", "N"},
            {"Motor electrico 75 kW", "1", "3", "N"},
            {"Motor electrico 30 kW", "1", "1", "N"},
            {"Cinta transportadora principal", "0", "3", "S"},
            {"Cinta transportadora secundaria", "0", "3", "N"},
            {"Hidrolavadora industrial", "0", "5", "N"},
            {"Generador diesel de emergencia", "1", "4", "S"},
            {"Montacargas electrico 2.5 t", "1", "5", "N"},
            {"Torre de enfriamiento", "4", "5", "S"},
            {"Chiller de agua helada", "4", "0", "S"},
            {"Horno de secado", "4", "3", "S"},
            {"Prensa hidraulica 100 t", "2", "3", "N"},
            {"Taladro radial", "0", "3", "N"},
            {"Sierra de cinta", "0", "3", "N"},
            {"Lijadora orbital", "0", "1", "N"},
            {"Tablero de control MCC-1", "1", "4", "S"},
            {"Tablero de control MCC-2", "1", "4", "N"},
            {"Transformador 500 kVA", "1", "4", "S"},
            {"Variador de frecuencia ABB", "1", "1", "N"},
            {"Sensor de temperatura PT100", "3", "2", "N"},
            {"Transmisor de presion", "3", "0", "N"},
            {"Celula fotoelectricas de etiquetado", "3", "1", "N"},
            {"Valvula de control neumatica", "2", "3", "N"},
            {"Cilindro neumatico 80 mm", "2", "1", "N"},
            {"Solenoides de seguridad", "2", "0", "N"},
            {"Etiquetadora automatica", "0", "1", "S"},
            {"Envasadora rotativa", "0", "2", "S"},
            {"Selladora de bandejas", "0", "2", "N"},
            {"Paletizador automatico", "0", "3", "N"},
            {"Caldera de tubos de humo", "4", "5", "S"},
            {"Bomba de alimentacion de caldera", "4", "5", "N"},
            {"Intercambiador de placas", "4", "3", "N"},
            {"Extractor de humos", "0", "3", "N"},
            {"Ventilador industrial axial", "0", "3", "N"},
            {"Parrilla de soldadura MIG", "1", "3", "N"},
            {"Cabina de pintura", "0", "3", "N"},
            {"Plataforma elevadora", "0", "5", "N"}
    };

    private List<Equipo> sembrarEquipos(List<CategoriaEquipo> categorias,
                                        List<Ubicacion> ubicaciones, Random r, LocalDate hoy) {
        String[] fabricantes = {"Atlas Copco", "Grundfos", "Siemens", "ABB", "WEG",
                "Schneider", "Festo", "SMC", "Ingersoll Rand", "Toshiba"};
        List<Equipo> lista = new ArrayList<>();
        for (int i = 0; i < EQUIPOS_BASE.length; i++) {
            String[] d = EQUIPOS_BASE[i];
            lista.add(equipoRepository.save(Equipo.builder()
                    .codigo(String.format("E-%03d", i + 1))
                    .nombre(d[0])
                    .categoria(categorias.get(Integer.parseInt(d[1])))
                    .ubicacion(ubicaciones.get(Integer.parseInt(d[2])))
                    .fabricante(fabricantes[i % fabricantes.length])
                    .modelo("M-" + (100 + r.nextInt(800)))
                    .numeroSerie("SN-" + (100000 + r.nextInt(899999)))
                    .fechaInstalacion(hoy.minusDays(400 + r.nextInt(3000)))
                    .estado(EstadoEquipo.OPERATIVO)
                    .critico("S".equals(d[3]))
                    .costoAdquisicion(BigDecimal.valueOf(8000 + r.nextInt(90000))
                            .setScale(2, RoundingMode.HALF_UP))
                    .observaciones(r.nextInt(3) == 0
                            ? "Equipo con historial de fallas recurrentes; refuerza la inspeccion mensual."
                            : null)
                    .build()));
        }
        // Un equipo dado de baja para mostrar el filtro de estados.
        lista.get(lista.size() - 1).setEstado(EstadoEquipo.BAJA);
        equipoRepository.save(lista.get(lista.size() - 1));
        return lista;
    }

    // ------------------------------------------------------------ repuestos

    /** {nombre, precio, stockMinimo, unidad, ubicacionAlmacen, proveedor} */
    private static final String[][] REPUESTOS_BASE = {
            {"Rodamiento 6205-2RS", "28.50", "5", "pza", "Estante A-1", "0"},
            {"Rodamiento 6206-2RS", "34.00", "5", "pza", "Estante A-1", "0"},
            {"Rodamiento 6309-2RS", "89.00", "3", "pza", "Estante A-1", "0"},
            {"Rodamiento 6310 C3", "115.00", "2", "pza", "Estante A-1", "0"},
            {"Rodamiento rigido de bolas 6208", "41.00", "4", "pza", "Estante A-1", "0"},
            {"Reten de flecha 35x52x8", "19.00", "6", "pza", "Estante A-1", "0"},
            {"Correa dentada SPB1600", "42.00", "4", "pza", "Estante A-2", "1"},
            {"Correa en V B-54", "26.00", "6", "pza", "Estante A-2", "1"},
            {"Correa transportadora lisa 2 ply", "180.00", "1", "m", "Rollo B-0", "1"},
            {"Sello mecanico 25 mm", "65.00", "3", "pza", "Estante B-1", "0"},
            {"Sello torico kit", "12.00", "10", "pza", "Estante B-1", "0"},
            {"Juego de sellos para bomba 3196", "210.00", "2", "kit", "Estante E-3", "0"},
            {"Aro de desgaste de bomba", "145.00", "2", "pza", "Estante E-3", "0"},
            {"Junta espiralada 6 pulg", "24.00", "8", "pza", "Estante E-3", "0"},
            {"Filtro de aceite hidraulico", "55.00", "4", "pza", "Estante B-2", "0"},
            {"Filtro de aire de compresor", "48.00", "4", "pza", "Estante B-2", "0"},
            {"Filtro separador de agua", "38.00", "5", "pza", "Estante B-2", "0"},
            {"Filtro de lubricador neumatico", "52.00", "4", "pza", "Estante D-2", "2"},
            {"Aceite hidraulico ISO 68 (20 L)", "210.00", "2", "cub", "Estante B-3", "0"},
            {"Aceite para compresor Roto-Xtend (18 L)", "385.00", "1", "cub", "Estante B-3", "0"},
            {"Grasa EP2 (18 kg)", "95.00", "2", "cub", "Estante B-3", "0"},
            {"Aceite para engranajes ISO 220 (20 L)", "245.00", "1", "cub", "Estante B-3", "0"},
            {"Refrigerante para chiller (20 L)", "260.00", "2", "cub", "Estante B-3", "0"},
            {"Contactor 3RT 65 A", "145.00", "3", "pza", "Estante C-1", "3"},
            {"Contactor 3RT 95 A", "210.00", "2", "pza", "Estante C-1", "3"},
            {"Protector termico LR2 D12", "95.00", "4", "pza", "Estante C-1", "3"},
            {"Bobina 24 V para contactor", "58.00", "5", "pza", "Estante C-1", "3"},
            {"Fusible NH-00 63 A", "18.00", "10", "pza", "Estante C-2", "3"},
            {"Fusible NH-1 100 A", "32.00", "8", "pza", "Estante C-2", "3"},
            {"Lampara LED industrial 50 W", "75.00", "6", "pza", "Estante C-3", "3"},
            {"Pulsador verde 22 mm", "15.00", "8", "pza", "Estante C-3", "3"},
            {"Pulsador rojo de paro de emergencia", "42.00", "4", "pza", "Estante C-3", "3"},
            {"Enchufe de seguridad M16", "8.00", "12", "pza", "Estante C-3", "3"},
            {"Variador de frecuencia 11 kW", "1850.00", "1", "pza", "Estante C-4", "3"},
            {"Enfriador de gabinete 230 V", "280.00", "1", "pza", "Estante C-4", "3"},
            {"Sensor fotoelectrico PNP", "68.00", "4", "pza", "Estante D-1", "3"},
            {"Termopar tipo K", "35.00", "6", "pza", "Estante D-1", "2"},
            {"Transmisor de presion 0-10 bar", "240.00", "2", "pza", "Estante D-1", "2"},
            {"Manometro 0-16 bar glicerina", "46.00", "5", "pza", "Estante D-1", "2"},
            {"Valvula solenoide 5/2 24 V", "88.00", "4", "pza", "Estante D-2", "2"},
            {"Valvula de control neumatica 4-20 mA", "420.00", "1", "pza", "Estante D-2", "2"},
            {"Cilindro neumatico ISO 6431 80x200", "165.00", "2", "pza", "Estante D-2", "2"},
            {"Manguera neumatica 8 mm (25 m)", "90.00", "2", "rollo", "Estante D-2", "2"},
            {"Electrodo de soldadura 7018 (caja)", "120.00", "3", "caja", "Estante E-1", "1"},
            {"Cable de soldadura 12 mm2 (rollo)", "310.00", "1", "rollo", "Estante E-1", "1"},
            {"Portaelectrodos 300 A", "55.00", "3", "pza", "Estante E-1", "1"},
            {"Antiparras de soldadura", "38.00", "5", "pza", "Estante E-1", "1"},
            {"Guante anticorte (par)", "22.00", "10", "par", "Estante E-1", "1"},
            {"Electroventilador axial 400 mm", "195.00", "2", "pza", "Estante E-2", "1"},
            {"Pastilla de freno de montacargas", "175.00", "4", "pza", "Estante E-2", "1"},
            {"Pinza de freno de montacargas", "240.00", "2", "pza", "Estante E-2", "1"},
            {"Cuerpo de repuesto para bomba centrifuga", "1250.00", "1", "pza", "Estante E-3", "0"},
            {"Cinta teflon (kit x10)", "14.00", "10", "kit", "Estante B-1", "2"},
            {"Bateria acida 12 V 100 Ah", "690.00", "1", "pza", "Estante C-4", "1"}
    };

    private List<Repuesto> sembrarRepuestos(List<Proveedor> proveedores, Random r) {
        List<Repuesto> lista = new ArrayList<>();
        for (int i = 0; i < REPUESTOS_BASE.length; i++) {
            String[] d = REPUESTOS_BASE[i];
            // El stock final se decide en sembrarKardexInicial; aqui solo semilla.
            lista.add(repuestoRepository.save(Repuesto.builder()
                    .codigo(String.format("RP-%03d", i + 1))
                    .nombre(d[0])
                    .descripcion(descripcionRepuesto(d[0], r))
                    .stock(0)
                    .stockMinimo(Integer.parseInt(d[2]))
                    .unidad(d[3])
                    .precioCosto(new BigDecimal(d[1]))
                    .ubicacionAlmacen(d[4])
                    .proveedor(proveedores.get(Integer.parseInt(d[5])))
                    .activo(true)
                    .build()));
        }
        return lista;
    }

    private String descripcionRepuesto(String nombre, Random r) {
        String[] aplicaciones = {"Repuesto normalizado, entrega inmediata del proveedor.",
                "Compatibilidad verificada con la ficha tecnica del equipo.",
                "Inspeccionar la medida antes de instalar.",
                "Stock minimo definido por el plan de mantenimiento."};
        return nombre + ". " + aplicaciones[r.nextInt(aplicaciones.length)];
    }

    // -------------------------------------------------------------- ordenes

    private static final String[][] TITULOS_PREVENTIVO = {
            {"Cambio de aceite y filtros", "Reemplazo de elementos filtrantes y verificacion de niveles."},
            {"Inspeccion de rodamientos", "Lubricacion, medicion de juego y control de temperatura."},
            {"Lubricacion general", "Aplicacion de grasa EP2 en puntos designados y limpieza."},
            {"Calibracion de sensores", "Verificacion contra patron y ajuste de cero."},
            {"Revision de correas y tensor", "Control de tension y alineacion de transmision."},
            {"Limpieza de enfriadores", "Desincrustacion de serpentines y prueba de caudal."},
            {"Prueba de protecciones", "Ensayo de relés, termicos y cortacircuitos."},
            {"Cambio de empaques", "Reemplazo de sellos y prueba de estanqueidad."}
    };

    private static final String[][] TITULOS_CORRECTIVO = {
            {"Fuga de aire en linea principal", "Se reporta pérdida de presion en la red neumatica."},
            {"Sobrecalentamiento del motor", "Temperatura por encima del limite durante la jornada."},
            {"Vibracion anormal en banco", "Se detecto vibracion fuera de rango con portatil."},
            {"Falla electrica intermitente", "Salidas de control sin orden en el MCC."},
            {"Bajo caudal en bomba", "El caudal medida esta por debajo del nominal."},
            {"Ruido en engranajes", "Ruido metalico en la caja de reduccion."},
            {"No arranque tras parada", "El equipo no respondio al comando de arranque."},
            {"Desalineacion de transmision", "Sobrecalentamiento y vibracion en el acople."}
    };

    private static final String[][] TITULOS_PREDICTIVO = {
            {"Termografia de tableros", "Lectura termografica de barras y contactores."},
            {"Analisis de vibraciones", "Captura de espectros en puntos de medicion."},
            {"Ultrasonido en rodamientos", "Deteccion temprana de defectos de pista."},
            {"Analisis de aceite", "Muestreo y laboratorio de propiedades del lubricante."}
    };

    private static final String[] SOLUCIONES = {
            "Se realizo el trabajo programado, se repusieron componentes desgastados y se probo el equipo en vacio y en carga. Sin observaciones.",
            "Causa raiz identificada: desgaste del componente. Se repuso, se alineo y se verifico el consumo electrico dentro de rango.",
            "Se corrigio la falla reportada, se dejo el equipo operativo y se recomienda inspeccion en 15 dias.",
            "Trabajo ejecutado completo, se actualizo la ficha del equipo y se entregaron repuestos sobrantes al almacen."
    };

    private void sembrarOrdenes(List<Equipo> equipos, List<Tecnico> tecnicos,
                                List<Repuesto> repuestos, Random r, LocalDate hoy) {
        int total = 150;
        List<Integer> diasAtras = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            diasAtras.add(r.nextInt(240));
        }
        diasAtras.sort(Collections.reverseOrder()); // mas antigua primero

        // consumo acumulado por repuesto para el kardex
        Map<Long, Integer> consumo = new HashMap<>();
        Map<Long, List<Object[]>> salidas = new HashMap<>(); // {cantidad, fecha, numero, creadoPor}

        for (int i = 0; i < total; i++) {
            int dias = diasAtras.get(i);
            LocalDateTime creacion = hoy.minusDays(dias)
                    .atTime(7 + r.nextInt(5), r.nextInt(60));

            TipoOrden tipo = tipoAlAzar(r);
            String[] contenidos = tituloAlAzar(tipo, r);
            Equipo equipo = equipos.get(r.nextInt(equipos.size()));
            if (equipo.isDeBaja()) {
                equipo = equipos.get(0);
            }

            OrdenTrabajo orden = OrdenTrabajo.builder()
                    .numero(String.format("OT-%04d", i + 1))
                    .equipo(equipo)
                    .tipo(tipo)
                    .prioridad(prioridadAlAzar(r, tipo))
                    .titulo(contenidos[0])
                    .descripcion(contenidos[1])
                    .fechaCreacion(creacion)
                    .fechaProgramada(creacion.toLocalDate().plusDays(r.nextInt(8)))
                    .creadoPor(r.nextInt(3) == 0 ? "jefe" : "admin")
                    .build();

            EstadoOrden estado = estadoAlAzar(dias, r);
            orden.setEstado(estado);

            boolean cierra = estado == EstadoOrden.COMPLETADA
                    || estado == EstadoOrden.CANCELADA;
            if (cierra || estado == EstadoOrden.EN_EJECUCION) {
                orden.setTecnico(tecnicos.get(r.nextInt(tecnicos.size())));
            } else if (r.nextInt(10) < 4) {
                orden.setTecnico(tecnicos.get(r.nextInt(tecnicos.size())));
            }

            if (estado == EstadoOrden.COMPLETADA) {
                LocalDateTime inicio = creacion.plusHours(4 + r.nextInt(60));
                LocalDateTime fin = inicio.plusHours(2 + r.nextInt(36));
                if (fin.isAfter(LocalDateTime.now())) {
                    fin = LocalDateTime.now().minusHours(1 + r.nextInt(6));
                    inicio = fin.minusHours(2 + r.nextInt(10));
                }
                BigDecimal horas = BigDecimal.valueOf(1 + r.nextInt(70) / 10.0)
                        .setScale(2, RoundingMode.HALF_UP);
                orden.setFechaInicio(inicio);
                orden.setFechaFin(fin);
                orden.setHorasManoObra(horas);
                orden.setCostoManoObra(horas.multiply(new BigDecimal("38.50"))
                        .setScale(2, RoundingMode.HALF_UP));
                orden.setSolucion(SOLUCIONES[r.nextInt(SOLUCIONES.length)]);
            } else if (estado == EstadoOrden.EN_EJECUCION) {
                LocalDateTime inicio = creacion.plusHours(2 + r.nextInt(30));
                if (inicio.isAfter(LocalDateTime.now().minusHours(2))) {
                    inicio = LocalDateTime.now().minusHours(2 + r.nextInt(6));
                }
                orden.setFechaInicio(inicio);
            } else if (estado == EstadoOrden.CANCELADA) {
                orden.setSolucion("Orden cancelada: " + (r.nextBoolean()
                        ? "el equipo quedo fuera de servicio por decision de operaciones."
                        : "el trabajo fue cubierto por una parada programada existente."));
            }

            // repuestos consumidos (solo ordenes cerradas con repuestos)
            if (estado == EstadoOrden.COMPLETADA && r.nextInt(100) < 55) {
                int lineas = 1 + r.nextInt(3);
                for (int d = 0; d < lineas; d++) {
                    Repuesto repuesto = repuestos.get(r.nextInt(repuestos.size()));
                    int cantidad = 1 + r.nextInt(3);
                    DetalleRepuesto detalle = DetalleRepuesto.builder()
                            .orden(orden)
                            .repuesto(repuesto)
                            .cantidad(cantidad)
                            .precioUnitario(repuesto.getPrecioCosto())
                            .build();
                    orden.getDetalles().add(detalle);

                    consumo.merge(repuesto.getId(), cantidad, Integer::sum);
                    salidas.computeIfAbsent(repuesto.getId(), k -> new ArrayList<>())
                            .add(new Object[]{cantidad, orden.getFechaCreacion(),
                                    orden.getNumero(), orden.getCreadoPor()});
                }
            }

            ordenRepository.save(orden);
        }

        // estado final de los equipos: en mantenimiento si tienen orden en ejecucion
        Set<Long> enMantenimiento = new HashSet<>();
        ordenRepository.findAll().stream()
                .filter(o -> o.getEstado() == EstadoOrden.EN_EJECUCION)
                .forEach(o -> enMantenimiento.add(o.getEquipo().getId()));
        for (Equipo e : equipos) {
            if (e.isDeBaja()) {
                continue;
            }
            if (enMantenimiento.contains(e.getId())) {
                e.setEstado(EstadoEquipo.EN_MANTENIMIENTO);
            } else if (e.getId().equals(equipos.get(6).getId())
                    || e.getId().equals(equipos.get(17).getId())) {
                e.setEstado(EstadoEquipo.DETENIDO);
            } else {
                e.setEstado(EstadoEquipo.OPERATIVO);
            }
            equipoRepository.save(e);
        }

        repuestoConsumo = consumo;
        repuestoSalidas = salidas;
    }

    private Map<Long, Integer> repuestoConsumo = Map.of();
    private Map<Long, List<Object[]>> repuestoSalidas = Map.of();

    // --------------------------------------------------------------- planes

    private static final String[] TAREAS_PLAN = {
            "Cambio de aceite y filtros",
            "Lubricacion y revision de rodamientos",
            "Inspeccion electrica y termografia",
            "Calibracion de instrumentos",
            "Revision de correas y alineacion"
    };

    private void sembrarPlanes(List<Equipo> equipos, List<Tecnico> tecnicos,
                               Random r, LocalDate hoy) {
        int[] frecuencias = {15, 30, 45, 60, 90};
        int creados = 0;
        for (int i = 0; i < equipos.size() && creados < 18; i++) {
            Equipo equipo = equipos.get(i);
            if (equipo.isDeBaja()) {
                continue;
            }
            int frecuencia = frecuencias[r.nextInt(frecuencias.length)];
            LocalDate proxima = hoy.plusDays(r.nextInt(70) - 10); // hasta 10 dias vencida
            planRepository.save(PlanPreventivo.builder()
                    .equipo(equipo)
                    .nombre(TAREAS_PLAN[creados % TAREAS_PLAN.length])
                    .frecuenciaDias(frecuencia)
                    .horasEstimadas(BigDecimal.valueOf(1 + r.nextInt(60) / 10.0)
                            .setScale(2, RoundingMode.HALF_UP))
                    .proximaFecha(proxima)
                    .ultimaOrden(proxima.minusDays(frecuencia))
                    .tecnico(tecnicos.get(r.nextInt(tecnicos.size())))
                    .activo(r.nextInt(10) > 0)
                    .observaciones("Plan generado a partir de la criticidad del equipo.")
                    .build());
            creados++;
        }
    }

    // --------------------------------------------------------------- kardex

    /**
     * Fija el stock final de cada repuesto y registra su historial: una entrada
     * inicial con toda la existencia y una salida por cada consumo de las ordenes,
     * de modo que el kardex quede cuadrado y con piezas en minimo o agotadas.
     */
    private void sembrarKardexInicial(List<Repuesto> repuestos, Random r, LocalDate hoy) {
        LocalDateTime apertura = hoy.minusDays(240).atTime(8, 0);
        for (Repuesto repuesto : repuestos) {
            int consumido = repuestoConsumo.getOrDefault(repuesto.getId(), 0);
            int base = r.nextInt(13); // existencia final (algunas en 0 = alerta de stock)
            int inicial = base + consumido;

            repuesto.setStock(base);
            repuestoRepository.save(repuesto);

            movimientoRepository.save(MovimientoRepuesto.builder()
                    .repuesto(repuesto)
                    .tipo(TipoMovimiento.ENTRADA)
                    .cantidad(inicial)
                    .stockResultante(inicial)
                    .fecha(apertura.minusHours(r.nextInt(48)))
                    .motivo("Stock inicial de apertura del almacen")
                    .registradoPor("admin")
                    .build());

            List<Object[]> salidas = new ArrayList<>(
                    repuestoSalidas.getOrDefault(repuesto.getId(), List.of()));
            salidas.sort(Comparator.comparing(o -> (LocalDateTime) o[1]));
            int saldo = inicial;
            for (Object[] s : salidas) {
                int cantidad = (int) s[0];
                LocalDateTime fecha = (LocalDateTime) s[1];
                String numero = (String) s[2];
                String creadoPor = (String) s[3];
                saldo -= cantidad;
                movimientoRepository.save(MovimientoRepuesto.builder()
                        .repuesto(repuesto)
                        .tipo(TipoMovimiento.SALIDA)
                        .cantidad(cantidad)
                        .stockResultante(Math.max(0, saldo))
                        .fecha(fecha.plusMinutes(30))
                        .motivo("Consumo en la " + numero)
                        .registradoPor(creadoPor == null ? "admin" : creadoPor)
                        .build());
            }
        }
    }

    // --------------------------------------------------------------- varios

    private TipoOrden tipoAlAzar(Random r) {
        int n = r.nextInt(100);
        if (n < 55) {
            return TipoOrden.PREVENTIVO;
        }
        if (n < 90) {
            return TipoOrden.CORRECTIVO;
        }
        return TipoOrden.PREDICTIVO;
    }

    private Prioridad prioridadAlAzar(Random r, TipoOrden tipo) {
        if (tipo == TipoOrden.CORRECTIVO) {
            int n = r.nextInt(100);
            if (n < 20) {
                return Prioridad.CRITICA;
            }
            if (n < 55) {
                return Prioridad.ALTA;
            }
            if (n < 85) {
                return Prioridad.MEDIA;
            }
            return Prioridad.BAJA;
        }
        int n = r.nextInt(100);
        if (n < 8) {
            return Prioridad.ALTA;
        }
        if (n < 55) {
            return Prioridad.MEDIA;
        }
        return Prioridad.BAJA;
    }

    private String[] tituloAlAzar(TipoOrden tipo, Random r) {
        String[][] banco = switch (tipo) {
            case PREVENTIVO -> TITULOS_PREVENTIVO;
            case CORRECTIVO -> TITULOS_CORRECTIVO;
            default -> TITULOS_PREDICTIVO;
        };
        return banco[r.nextInt(banco.length)];
    }

    private EstadoOrden estadoAlAzar(int dias, Random r) {
        int n = r.nextInt(100);
        if (dias > 14) {
            if (n < 76) {
                return EstadoOrden.COMPLETADA;
            }
            if (n < 84) {
                return EstadoOrden.CANCELADA;
            }
            if (n < 94) {
                return EstadoOrden.ABIERTA; // vencida: fechaProgramada en el pasado
            }
            return EstadoOrden.EN_EJECUCION;
        }
        if (n < 45) {
            return EstadoOrden.COMPLETADA;
        }
        if (n < 55) {
            return EstadoOrden.CANCELADA;
        }
        if (n < 85) {
            return EstadoOrden.ABIERTA;
        }
        return EstadoOrden.EN_EJECUCION;
    }
}
