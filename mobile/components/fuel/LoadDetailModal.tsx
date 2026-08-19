import { useState } from 'react';
import {
  ActivityIndicator,
  Image,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { colors, fonts, radius } from '../../constants/theme';
import {
  combustibleLabel,
  formatFecha,
  formatMoney,
  iconForTipoVehiculo,
} from '../../constants/labels';
import type { TipoCombustible, TipoVehiculo } from '../../services/vehiculos';

/**
 * Una carga ya resuelta contra los catálogos (proveedor, monto, identificador),
 * lista para mostrarse. Lleva más de lo que muestra la card colapsada: el resto
 * alimenta este detalle.
 *
 * El tipo vive ACÁ, con el componente que lo consume, y no en la pantalla del
 * historial: el detalle lo usan tanto el operario como el panel del admin, y un
 * componente compartido no puede depender de una pantalla puntual.
 */
export type Row = {
  id: number;
  identificador: string;
  tipoVehiculo: TipoVehiculo;
  tipoCombustible: TipoCombustible | null;
  fecha: string;
  fechaCarga: string;
  proveedor: string;
  litros: number;
  precioUnitario: number;
  costo: number;
  ticketFotoUrl: string | null;
  // Solo lo completa el panel del admin: en el historial del operario los
  // tickets anulados no aparecen.
  anuladoPor?: string | null;
  fechaAnulacion?: string | null;
};

/**
 * Detalle de una carga del historial. Se abre al tocar una card (que colapsada
 * solo muestra litros y total). Presenta los datos completos y una
 * previsualización de la foto del ticket cuando existe.
 *
 * `row === null` mantiene el modal cerrado, así el padre controla la visibilidad
 * con un único estado (la fila seleccionada).
 */
export function LoadDetailModal({ row, onClose }: { row: Row | null; onClose: () => void }) {
  return (
    <Modal visible={row != null} transparent animationType="slide" onRequestClose={onClose}>
      <Pressable style={styles.overlay} onPress={onClose}>
        {/* key por ticket: remonta el contenido en cada apertura, así el estado de
            carga de la imagen arranca limpio y no se arrastra entre cargas. */}
        {row && <DetailSheet key={row.id} row={row} onClose={onClose} />}
      </Pressable>
    </Modal>
  );
}

function DetailSheet({ row, onClose }: { row: Row; onClose: () => void }) {
  // Estado de carga de la imagen presignada: da feedback mientras baja y permite
  // mostrar un aviso si la URL expiró o falla.
  const [imgLoading, setImgLoading] = useState(true);
  const [imgError, setImgError] = useState(false);
  const Icon = iconForTipoVehiculo(row.tipoVehiculo);

  return (
    <>
      {/* Frena la propagación: tocar la tarjeta no cierra el modal. */}
      <Pressable style={styles.sheet} onPress={() => {}}>
        <View style={styles.handle} />

        <View style={styles.header}>
          <View style={styles.headerLeft}>
            <View style={styles.icon}>
              <Icon width={20} height={20} color={colors.primary} />
            </View>
            <View>
              <Text style={styles.identificador}>{row.identificador}</Text>
              <Text style={styles.fecha}>{formatFecha(row.fechaCarga)}</Text>
            </View>
          </View>
          <Pressable
            onPress={onClose}
            hitSlop={12}
            accessibilityRole="button"
            accessibilityLabel="Cerrar el detalle de la carga"
          >
            <Text style={styles.close}>✕</Text>
          </Pressable>
        </View>

        <ScrollView showsVerticalScrollIndicator={false}>
          <View style={styles.ticketBox}>
            {row.ticketFotoUrl && !imgError ? (
              <>
                <Image
                  source={{ uri: row.ticketFotoUrl }}
                  style={styles.ticketImg}
                  resizeMode="contain"
                  onLoadEnd={() => setImgLoading(false)}
                  onError={() => {
                    setImgLoading(false);
                    setImgError(true);
                  }}
                />
                {imgLoading && (
                  <ActivityIndicator color={colors.primary} style={styles.imgSpinner} />
                )}
              </>
            ) : (
              <View style={styles.ticketPlaceholder}>
                <Text style={styles.placeholderIcon}>🧾</Text>
                <Text style={styles.placeholderText}>
                  {imgError ? 'No se pudo cargar la foto del ticket.' : 'Sin foto del ticket.'}
                </Text>
              </View>
            )}
          </View>

          <DetailRow label="Proveedor" value={row.proveedor} />
          <DetailRow
            label="Combustible"
            value={row.tipoCombustible ? combustibleLabel[row.tipoCombustible] : '—'}
          />
          <DetailRow label="Litros" value={`${row.litros} L`} mono />
          <DetailRow label="Precio / L" value={formatMoney(row.precioUnitario)} mono />

          <View style={styles.totalRow}>
            <Text style={styles.totalLabel}>TOTAL</Text>
            <Text style={styles.totalValue}>{formatMoney(row.costo)}</Text>
          </View>
        </ScrollView>
      </Pressable>
    </>
  );
}

function DetailRow({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <View style={styles.detailRow}>
      <Text style={styles.detailLabel}>{label}</Text>
      <Text style={[styles.detailValue, mono && { fontFamily: fonts.mono }]}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.6)', justifyContent: 'flex-end' },
  sheet: {
    backgroundColor: colors.bg,
    borderTopLeftRadius: 22,
    borderTopRightRadius: 22,
    paddingHorizontal: 18,
    paddingBottom: 28,
    paddingTop: 10,
    maxHeight: '88%',
  },
  handle: {
    alignSelf: 'center',
    width: 40,
    height: 4,
    borderRadius: 2,
    backgroundColor: colors.borderSoft,
    marginBottom: 14,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 16,
  },
  headerLeft: { flexDirection: 'row', alignItems: 'center', gap: 11 },
  icon: {
    width: 40,
    height: 40,
    borderRadius: 10,
    backgroundColor: colors.primary + '1A',
    alignItems: 'center',
    justifyContent: 'center',
  },
  identificador: { fontFamily: fonts.displayBold, fontSize: 17, color: colors.text },
  fecha: { fontSize: 11.5, color: colors.textFaint, fontFamily: fonts.mono, marginTop: 2 },
  close: { fontSize: 18, color: colors.textMuted },

  ticketBox: {
    height: 260,
    borderRadius: radius.md,
    backgroundColor: colors.bgBlack,
    borderWidth: 1,
    borderColor: colors.border,
    overflow: 'hidden',
    marginBottom: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  ticketImg: { width: '100%', height: '100%' },
  imgSpinner: { position: 'absolute' },
  ticketPlaceholder: { alignItems: 'center', gap: 8, padding: 20 },
  placeholderIcon: { fontSize: 34 },
  placeholderText: {
    color: colors.textDim,
    fontSize: 13,
    fontFamily: fonts.sans,
    textAlign: 'center',
  },

  detailRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: colors.divider,
  },
  detailLabel: { fontSize: 13, color: colors.textFaint, fontFamily: fonts.sans },
  detailValue: { fontSize: 14.5, color: colors.text, fontFamily: fonts.sansSemi },

  totalRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginTop: 18,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 13,
    paddingHorizontal: 16,
    paddingVertical: 15,
  },
  totalLabel: { fontSize: 12, color: colors.textFaint, letterSpacing: 1, fontFamily: fonts.sans },
  totalValue: { fontFamily: fonts.mono, fontSize: 24, color: colors.primary },
});
