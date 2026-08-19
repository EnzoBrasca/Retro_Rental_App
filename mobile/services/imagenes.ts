import { ImageManipulator, SaveFormat } from 'expo-image-manipulator';

/**
 * Ancho al que se reduce la foto del ticket antes de subirla.
 *
 * Un ticket de surtidor es legible de sobra a 1600 px de ancho, y el archivo
 * pasa de 1,5-3 MB a ~200-400 KB.
 */
const ANCHO_MAX = 1600;

/** Compresión JPEG posterior al redimensionado. */
const CALIDAD = 0.7;

/**
 * Reduce y comprime la foto de un ticket antes de subirla.
 *
 * POR QUÉ EXISTE. La cámara y la galería se llamaban con `quality: 0.6`, que
 * ajusta la compresión JPEG pero NO la resolución: una cámara de 12 MP entrega
 * 4000×3000 y al 60% sigue pesando entre 1,5 y 3 MB. Y cada foto se sube dos
 * veces —una a `/tickets/analyze` para el OCR y otra a `/tickets` al confirmar—,
 * así que eran 3 a 6 MB por carga, sobre la conexión de un yacimiento y con el
 * operario esperando con el ticket en la mano.
 *
 * Se llama UNA vez, apenas se toma o elige la foto, así las dos subidas usan el
 * mismo archivo ya reducido.
 *
 * Si la compresión falla se devuelve el original: subir pesado es mucho mejor
 * que dejar al operario sin poder registrar la carga.
 */
export async function comprimirTicket(uri: string): Promise<string> {
  try {
    // Se redimensiona por ANCHO y se deja que el alto siga la proporción. Un
    // ticket se fotografía casi siempre vertical, así que el ancho es el lado
    // corto y acotarlo es lo que más pesa en el tamaño final.
    const imagen = await ImageManipulator.manipulate(uri)
      .resize({ width: ANCHO_MAX })
      .renderAsync();
    const resultado = await imagen.saveAsync({ compress: CALIDAD, format: SaveFormat.JPEG });
    return resultado.uri;
  } catch {
    return uri;
  }
}
