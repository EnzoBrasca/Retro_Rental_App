import {
  consumoParaStats,
  etiquetaLectura,
  sufijoConsumo,
  textoBusquedaVehiculo,
  tituloVehiculo,
  unidadDeTipo,
  type Vehiculo,
} from './vehiculos';

/**
 * Logica pura del dominio de vehiculos: unidades, titulos y consumo.
 *
 * POR QUE ESTE ARCHIVO. `consumoParaStats` invierte una medida (L/100km -> km/L)
 * y la muestra al admin en la pantalla donde se decide plata. Un error ahi no
 * rompe nada: muestra un numero plausible y equivocado. Lo mismo vale para las
 * etiquetas de unidad — rotular horas como kilometros hace que el operario
 * anote el dato incorrecto y corrompa el calculo de consumo real.
 */

// Vehiculo minimo para los helpers que reciben la entidad entera. Solo importan
// los campos que la funcion bajo prueba realmente lee.
function vehiculo(over: Partial<Vehiculo> = {}): Vehiculo {
  return {
    id: 1,
    identificador: 'AA123BB',
    modelo: null,
    tipoVehiculo: 'CAMIONETA',
    tipoCombustible: 'GASOIL_GRADO_2',
    estado: 'DISPONIBLE',
    capacidadTanque: 80,
    usoAcumulado: 1000,
    unidadUso: 'KM',
    consumoPromedio: 12,
    consumoReciente: null,
    fechaUltimoMantenimiento: '2026-01-15',
    fechaBaja: null,
    idOperario: null,
    operarioNombre: null,
    operarioApellido: null,
    ...over,
  };
}

describe('unidadDeTipo', () => {
  it('mide una maquina vial en horas', () => {
    expect(unidadDeTipo('MAQUINA')).toBe('HORAS');
  });

  it('mide camiones y camionetas en kilometros', () => {
    expect(unidadDeTipo('CAMION')).toBe('KM');
    expect(unidadDeTipo('CAMIONETA')).toBe('KM');
  });
});

describe('etiquetaLectura', () => {
  // Nombra el INSTRUMENTO a proposito: es lo que evita que alguien anote
  // kilometros en una maquina que mide horas.
  it('pide horas del horometro en una maquina', () => {
    expect(etiquetaLectura('MAQUINA')).toBe('Horas del horómetro');
  });

  it('pide kilometros del odometro en un camion', () => {
    expect(etiquetaLectura('CAMION')).toBe('Kilómetros del odómetro');
  });
});

describe('consumoParaStats', () => {
  // Una maquina se expresa en L/h y NO se invierte.
  it('deja el consumo de una maquina en L/h sin invertir', () => {
    expect(consumoParaStats(18, 'HORAS')).toBe('18 L/h');
  });

  // km/L = 100 / (L/100km). Son la misma medida dada vuelta.
  it('invierte L/100km a km/L en un vehiculo de ruta', () => {
    expect(consumoParaStats(10, 'KM')).toBe('10.00 km/L');
    expect(consumoParaStats(12.5, 'KM')).toBe('8.00 km/L');
  });

  it('redondea a dos decimales', () => {
    expect(consumoParaStats(7, 'KM')).toBe('14.29 km/L');
  });

  // EL CASO QUE IMPORTA. El backend redondea a 2 decimales, asi que un consumo
  // muy bajo llega como 0. Sin la guarda, 100/0 da Infinity y la tarjeta del
  // panel mostraria "Infinity km/L".
  it('no divide por cero: muestra un guion en vez de Infinity', () => {
    expect(consumoParaStats(0, 'KM')).toBe('—');
  });

  it('tampoco divide con un consumo negativo', () => {
    expect(consumoParaStats(-1, 'KM')).toBe('—');
  });
});

describe('sufijoConsumo', () => {
  it('usa L/h en horas y L/100km en kilometros', () => {
    expect(sufijoConsumo('HORAS')).toBe('L/h');
    expect(sufijoConsumo('KM')).toBe('L/100km');
  });
});

describe('tituloVehiculo', () => {
  it('en una maquina antepone el modelo al interno', () => {
    const v = vehiculo({ tipoVehiculo: 'MAQUINA', modelo: 'CAT 320D', identificador: 'M-01' });
    expect(tituloVehiculo(v)).toBe('CAT 320D (M-01)');
  });

  // El fallback importa: una maquina cargada antes de que existiera el campo
  // modelo no tiene ninguno, y sin esto el titulo quedaria en "(M-01)".
  it('en una maquina sin modelo cae al identificador solo', () => {
    const v = vehiculo({ tipoVehiculo: 'MAQUINA', modelo: null, identificador: 'M-01' });
    expect(tituloVehiculo(v)).toBe('M-01');
  });

  // En un camion o camioneta manda la patente: es lo que esta pintado encima.
  it('en un camion muestra la patente aunque tenga modelo', () => {
    const v = vehiculo({ tipoVehiculo: 'CAMION', modelo: 'Hilux', identificador: 'AA123BB' });
    expect(tituloVehiculo(v)).toBe('AA123BB');
  });
});

describe('textoBusquedaVehiculo', () => {
  // Se busca tambien por modelo porque es lo que el operario ve como titulo de
  // la card: tipear "cat" tiene que encontrar la maquina que tiene delante.
  it('incluye identificador y modelo, en minusculas', () => {
    const v = vehiculo({ identificador: 'M-01', modelo: 'CAT 320D' });
    expect(textoBusquedaVehiculo(v)).toBe('m-01 cat 320d');
  });

  it('no escribe "null" cuando no hay modelo', () => {
    const v = vehiculo({ identificador: 'M-01', modelo: null });
    expect(textoBusquedaVehiculo(v)).not.toContain('null');
  });
});
